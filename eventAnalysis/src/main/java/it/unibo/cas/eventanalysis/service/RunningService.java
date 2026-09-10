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
import java.util.concurrent.TimeUnit;

/**
 * Main execution engine for the crowd analysis microservice.
 *
 * Responsibilities:
 * - Runs a dedicated background worker thread to process incoming sensor data without blocking application startup.
 * - Ingests probe batches from the subscriber queue, validates timestamps, and filters weak signals by RSSI.
 * - Manages an in-memory sliding window buffer of recent batches.
 * - Periodically purges expired batches and runs crowd estimation calculations every slide step interval.
 * - Triggers dual-path alerts when dangerous crowd growth patterns are detected.
 * - Sends completed analysis statistics to the central Event Management backend service.
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

    /**
     * Starts the subscriber client and spawns the background analysis thread when the Spring application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startAnalysisLoop() {
        subscriber.start();

        Thread analysisThread = new Thread(this::runLoop, "AnalysisLoopThread");
        analysisThread.start();
    }

    /**
     * Continuous background loop executing batch ingestion and periodic analysis.
     * Consumes batches from the queue, drops obsolete batches, applies RSSI filtering,
     * buffers valid data, purges expired batches when the slide timer expires,
     * and coordinates alert checks and backend reporting.
     */
    private void runLoop() {
        log.info("[AREA {}] Starting analysis area={} | broker {}:{} | topic={}",
                area.id(), config.areaId(), config.mqttHost(), config.mqttPort(), config.topicProbes());

        if (!subscriber.waitConnected(15, TimeUnit.SECONDS)) {
            log.warn("[AREA {}] Broker unreachable during startup: will keep trying in background", area.id());
        }

        double windowSeconds = analysisService.getWindowSize(); 
        double stepSeconds = analysisService.getSlideStep();    

        long stepNanos = (long) (stepSeconds * 1_000_000_000L);
        long nextStats = System.nanoTime() + stepNanos;

        while(running && !Thread.currentThread().isInterrupted()) {
            ProbeBatch batch = subscriber.get(1, TimeUnit.SECONDS);

            if (batch != null) {
                subscriber.ack(batch);

                OffsetDateTime now = OffsetDateTime.now();
                OffsetDateTime batchTime = batch.getSentAt() != null ? batch.getSentAt() : now;
                long ageSeconds = Duration.between(batchTime, now).getSeconds();

                if (ageSeconds > (long) windowSeconds || ageSeconds < -5) {
                    log.warn("[AREA {}] STALE batch id={} (age: {}s > window: {}s) - Discarded from calculation",
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
                nextStats = currentNanos + stepNanos;

                log.info("[AREA {}] Window timer expired. Total accumulated batches in window: {}", area.id(), probeBatches.size());
                log.info("[AREA {}] Transport stats: {}", area.id(), subscriber.getStatsSnapshot());
                

                removeExpiredBatches(windowSeconds);

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

                }
            }
        }

        log.info("[AREA {}] Final summary: {}", area.id(), subscriber.getStatsSnapshot());
    }
    
    /**
     * Removes batches from the in-memory buffer whose generation timestamp is older than the configured window size.
     *
     * @param windowSeconds the time window duration in seconds
     */
    private void removeExpiredBatches(double windowSeconds) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds((long) windowSeconds);
        int initialSize = probeBatches.size();

        probeBatches.removeIf(b -> b.getSentAt() != null && b.getSentAt().isBefore(cutoff));

        int removedCount = initialSize - probeBatches.size();
        if (removedCount > 0) {
            log.info("[AREA {}] Purged {} expired batches from window buffer", area.id(), removedCount);
        }
    }

    /**
     * Executes crowd estimation on the currently buffered batches, computes crowd density,
     * updates the analysis history, and evaluates the crowd growth trend.
     *
     * @return the computed {@link AnalysisStats} record
     */
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

    /**
     * Gracefully stops the worker loop and shuts down the subscriber when the Spring context is destroyed.
     */
    @PreDestroy
    public void stop() {
        log.info("Arresto graceful di RunningService per area={}...", config.areaId());
        this.running = false;
        if (subscriber != null) {
            subscriber.stop();
        }
    }
}
