package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import it.unibo.cas.eventanalysis.messaging.ProbeSubscriber;
import it.unibo.cas.eventanalysis.models.entities.AnalysisHistory;
import it.unibo.cas.eventanalysis.models.entities.AnalysisStats;
import it.unibo.cas.eventanalysis.models.DTOs.AnalysisStatsDTO;
import it.unibo.cas.eventanalysis.models.entities.Area;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;
import it.unibo.cas.eventanalysis.models.enums.Trend;
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

    @Setter
    @Getter
    private ArrayList<ProbeBatch> probeBatches = new ArrayList<>();
    
    private final AnalysisHistory analysisHistory = new AnalysisHistory();
    private ProbeBatch lastProcessedBatch = null;

    @Autowired
    private BatchService batchService;
    @Autowired
    private Area area;

    @EventListener(ApplicationReadyEvent.class)
    public void startAnalysisLoop() {
        // Start in a separate thread to not block Spring Boot startup
        Thread analysisThread = new Thread(this::runLoop, "AnalysisLoopThread");
        analysisThread.start();
    }

    private void runLoop() {
        log.info("Starting analysis area={} | broker {}:{} | topic={}",
                config.areaId(), config.mqttHost(), config.mqttPort(), config.topicProbes());

        subscriber.start();

        if (!subscriber.waitConnected(15, TimeUnit.SECONDS)) {
            log.warn("Broker unreachable: will keep trying in background");
        }

        long nextStats = System.nanoTime() + (long) (analysisService.getWindowSize() * 1_000_000_000L);

        while (!subscriber.isStopping() && !Thread.currentThread().isInterrupted()) {
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
                log.info("transport: {}", subscriber.getStatsSnapshot());
                nextStats = now + (long) (analysisService.getWindowSize() * 1_000_000_000L);

                AnalysisStats analysisStats = doAnalysis();
                
                alertService.checkAlerts(analysisHistory);


                AnalysisStatsDTO analysisStatsDTO = AnalysisStatsDTO.builder()
                        .node(kubernetesService.getNodeName())
                        .area_id(area.id())
                        .ts(OffsetDateTime.now())
                        .trend(analysisStats.getTrend())
                        .served_by("analysis-"+area.id())
                        .estimatedPeople(analysisStats.getEstimatedPeople())
                        .density(analysisStats.getDensity())
                        .build();


                log.info("First analysis! Data was send: {}",analysisStatsDTO.toString());
                eventManagementClient.sendAnalysis(analysisStatsDTO);
                // Clear the sliding window after the analysis cycle
                probeBatches.clear();
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
}
