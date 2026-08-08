package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import it.unibo.cas.eventanalysis.messaging.ProbeSubscriber;
import it.unibo.cas.eventanalysis.models.entities.*;
import it.unibo.cas.eventanalysis.models.DTOs.AnalysisStatsDTO;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the analysis service. For now: connects to the broker
 * and consumes batches. Windowing and analysis will be injected in this loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningService {

    private final AnalysisProperties config;
    private final ProbeSubscriber subscriber;

    @Autowired
    private AnalysisService analysisService;
    
    @Autowired
    private AlertService alertService;

    @Autowired
    private EventManagementClient eventManagementClient;

    @Autowired
    private KubernetesService kubernetesService;
    
    @Autowired
    private BatchService batchService;
    
    @Autowired
    private Area area;
    
    @Setter
    @Getter
    private ArrayList<ProbeBatch> probeBatches = new ArrayList<>();
    
    private final AnalysisHistory analysisHistory = new AnalysisHistory();
    private ProbeBatch lastProcessedBatch = null;

    private volatile boolean running = true;
    private long lastAlertNanos = 0;

    @EventListener(ApplicationReadyEvent.class)
    public void startAnalysisLoop() {
        subscriber.start();

        // Start in a separate thread to not block Spring Boot startup
        Thread analysisThread = new Thread(this::runLoop, "AnalysisLoopThread");
        analysisThread.start();
    }

    private void runLoop() {
        log.info("Starting analysis area={} | broker {}:{} | topic={}",
                config.areaId(), config.mqttHost(), config.mqttPort(), config.topicProbes());

        if (!subscriber.waitConnected(15, TimeUnit.SECONDS)) {
            log.warn("Broker unreachable during startup: will keep trying in background");
        }

        long windowNanos = (long) (analysisService.getWindowSize() * 1_000_000_000L);
        long nextStats = System.nanoTime() + windowNanos;

        while(running && !Thread.currentThread().isInterrupted()) {
            ProbeBatch batch = subscriber.get(1, TimeUnit.SECONDS);

            if (batch != null) {
                log.info("batch={} probes={} (declared {}, malformed {}) latency={} ms",
                        batch.getBatchId(), batch.getCountEffective(), batch.getCountDeclared(),
                        batch.getMalformedProbes(),
                        String.format(java.util.Locale.US, "%.1f", batch.getTransportLatencyMs()));
                
                subscriber.ack(batch);

                if (!batchService.batchIsLast(batch, lastProcessedBatch))
                    log.warn("one or more batching missing! Last batch id: {} - New batch id: {}",
                            lastProcessedBatch != null ? lastProcessedBatch.getBatchId() : "none", batch.getBatchId());

                batch = batchService.filterRSSI(batch);
                probeBatches.add(batch);
                lastProcessedBatch = batch;
            }

            long now = System.nanoTime();
            if (now >= nextStats) {
                log.info("Transport stats: {}", subscriber.getStatsSnapshot());
                windowNanos = (long) (analysisService.getWindowSize() * 1_000_000_000L);
                nextStats = now + windowNanos;

                if (!probeBatches.isEmpty()) {
                    AnalysisStats analysisStats = doAnalysis();

                    Alert alert = alertService.checkAlerts(analysisHistory);
                    if (alert != null) {
                        // Rate limiting alert: attende almeno 3 finestre temporali prima del successivo invio
                        if (lastAlertNanos == 0 || (now - lastAlertNanos) >= 3 * windowNanos) {
                            alertService.sendAlert(alert);
                            lastAlertNanos = now;
                        }
                    }

                    AnalysisStatsDTO analysisStatsDTO = AnalysisStatsDTO.builder()
                            .node(kubernetesService.getNodeName())
                            .area_id(area.id())
                            .ts(OffsetDateTime.now())
                            .trend(analysisStats.getTrend())
                            .served_by("analysis-" + area.id())
                            .estimatedPeople(analysisStats.getEstimatedPeople())
                            .density(analysisStats.getDensity())
                            .build();

                    log.info("Analysis cycle completed! Sending data to EventManagement: {}", analysisStatsDTO);
                    try {
                        eventManagementClient.sendAnalysis(analysisStatsDTO);
                    } catch (Exception e) {
                        log.error("Failed to send analysis stats to EventManagement backend: {}", e.getMessage());
                    }

                    // Pulisce la finestra scorrevole per il ciclo successivo
                    probeBatches.clear();
                }
            }
        }

        log.info("Final summary: {}", subscriber.getStatsSnapshot());
    }
    
    private AnalysisStats doAnalysis() {
        long estimatedPeople = analysisService.estimatePeople(probeBatches);
        double density = analysisService.density(estimatedPeople);
        double latency = 0.0;
        
        AnalysisStats analysisStats = AnalysisStats.builder()
                .latency(latency)
                .estimatedPeople(estimatedPeople)
                .density(density)
                .build();
                
        analysisHistory.addAnalysisStats(analysisStats);
        
        Trend trend = analysisService.calculateTrend(analysisHistory.getAnalysisStats());
        analysisStats.setTrend(trend);
        
        return analysisStats;
    }

    @PreDestroy
    public void stop() {
        log.info("Arresto graceful di RunningService per area={}...", config.areaId());
        this.running = false;
        if (subscriber != null) {
            subscriber.stop();
        }
    }
}
