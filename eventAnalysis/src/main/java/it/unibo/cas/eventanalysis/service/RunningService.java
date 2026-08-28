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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
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
        log.info("[AREA {}] Starting analysis area={} | broker {}:{} | topic={}",
                area.id(), config.areaId(), config.mqttHost(), config.mqttPort(), config.topicProbes());

        if (!subscriber.waitConnected(15, TimeUnit.SECONDS)) {
            log.warn("[AREA {}] Broker unreachable during startup: will keep trying in background", area.id());
        }

        double windowSeconds = analysisService.getWindowSize();
        long windowNanos = (long) (analysisService.getWindowSize() * 1_000_000_000L);
        long nextStats = System.nanoTime() + windowNanos;

        while(running && !Thread.currentThread().isInterrupted()) {
            ProbeBatch batch = subscriber.get(1, TimeUnit.SECONDS);

            if (batch != null) {
                subscriber.ack(batch);

                OffsetDateTime now = OffsetDateTime.now();
                OffsetDateTime batchTime = batch.getSentAt() != null ? batch.getSentAt() : now;
                long ageSeconds = Duration.between(batchTime, now).getSeconds();

                // Filtro temporale, se batch è più vecchio della finestra di osservazione W è un residuo
                if (ageSeconds > (long) windowSeconds || ageSeconds < -5) {
                    log.warn("[AREA {}] Drained STALE batch id={} (age: {}s > window: {}s) - Discarded from calculation",
                            area.id(), batch.getBatchId(), ageSeconds, (long) windowSeconds);
                    lastProcessedBatch = batch;
                    continue;
                }

                int rawProbeCount = batch.getProbes() != null ? batch.getProbes().size() : 0;

                if (!batchService.batchIsLast(batch, lastProcessedBatch)) {
                    log.warn("[AREA {}] ONE OR MORE BATCHES MISSING! Last batch id: {} - New batch id: {}",
                            area.id(), lastProcessedBatch != null ? lastProcessedBatch.getBatchId() : "none", batch.getBatchId());
                }

                // Log dettagliato prima e dopo il filtro RSSI
                batch = batchService.filterRSSI(batch);
                int filteredProbeCount = batch.getProbes() != null ? batch.getProbes().size() : 0;

                log.info("[AREA {}] Received Batch id={} | Raw Probes={} | Probes after RSSI filter={} | Latency={} ms",
                        area.id(), batch.getBatchId(), rawProbeCount, filteredProbeCount,
                        String.format(java.util.Locale.US, "%.1f", batch.getTransportLatencyMs()));
                
                
                probeBatches.add(batch);
                lastProcessedBatch = batch;
            }

            long currentNanos = System.nanoTime();
            if (currentNanos >= nextStats) {
                log.info("[AREA {}] Window timer expired. Total accumulated batches in window: {}", area.id(), probeBatches.size());
                log.info("[AREA {}] Transport stats: {}", area.id(), subscriber.getStatsSnapshot());
                windowNanos = (long) (analysisService.getWindowSize() * 1_000_000_000L);
                nextStats = currentNanos + windowNanos;

                purgeExpiredBatches(analysisService.getWindowSize());

                if (!probeBatches.isEmpty()) {
                    AnalysisStats analysisStats = doAnalysis();

                    Alert alert = alertService.checkAlerts(analysisHistory);
                    if (alert != null) {
                        alertService.emitDualPathAlert(alert);
                    }
                    
                    AnalysisStatsDTO analysisStatsDTO = AnalysisStatsDTO.builder()
                            .node(kubernetesService.getNodeName())
                            .area_id(area.id())
                            .ts(OffsetDateTime.now())
                            .window_seconds(analysisService.getWindowSize())
                            .trend(analysisStats.getTrend())
                            .served_by("analysis-" + area.id())
                            .estimatedPeople(analysisStats.getEstimatedPeople())
                            .density(analysisStats.getDensity())
                            .build();

                    log.info("[AREA {}] Analysis cycle completed! Sending stats to EventManagement: estimatedPeople={}, density={}, trend={}", 
                        area.id(), analysisStats.getEstimatedPeople(), analysisStats.getDensity(), analysisStats.getTrend());
                
                    try {
                        eventManagementClient.sendAnalysis(analysisStatsDTO);
                    } catch (Exception e) {
                        log.error("[AREA {}] Failed to send analysis stats to EventManagement backend: {}", area.id(), e.getMessage());
                    }

                    // Pulisce la finestra scorrevole per il ciclo successivo
                    probeBatches.clear();
                }
            }
        }

        log.info("[AREA {}] Final summary: {}", area.id(), subscriber.getStatsSnapshot());
    }
    
    /**
     * Rimuove dalla memoria i batch il cui timestamp reale è più vecchio di windowSeconds rispetto ad ora.
     */
    private void purgeExpiredBatches(double windowSeconds) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds((long) windowSeconds);
        int initialSize = probeBatches.size();

        // Rimuove in un solo passaggio tutti i batch inviati prima del tempo limite
        probeBatches.removeIf(b -> b.getSentAt() != null && b.getSentAt().isBefore(cutoff));

        int removedCount = initialSize - probeBatches.size();
        if (removedCount > 0) {
            log.info("[AREA {}] Purged {} expired batches from window buffer", area.id(), removedCount);
        }
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
