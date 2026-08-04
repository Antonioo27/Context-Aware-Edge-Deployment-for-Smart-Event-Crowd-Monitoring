package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import it.unibo.cas.eventanalysis.messaging.ProbeSubscriber;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Entry point of the analysis service. For now: connects to the broker
 * and consumes batches. Windowing and analysis will be injected in this loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisProperties config;
    private final ProbeSubscriber subscriber;

    private static final double STATS_INTERVAL_S = 30.0;

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

        long nextStats = System.nanoTime() + (long) (STATS_INTERVAL_S * 1_000_000_000L);

        while (!subscriber.isStopping() && !Thread.currentThread().isInterrupted()) {
            ProbeBatch batch = subscriber.get(1, TimeUnit.SECONDS);

            if (batch != null) {
                log.info("batch={} probes={} (declared {}, malformed {}) latency={} ms",
                        batch.getBatchId(), batch.getCountEffective(), batch.getCountDeclared(),
                        batch.getMalformedProbes(),
                        String.format(java.util.Locale.US, "%.1f", batch.getTransportLatencyMs()));

                subscriber.ack(batch);
            }

            long now = System.nanoTime();
            if (now >= nextStats) {
                log.info("transport: {}", subscriber.getStatsSnapshot());
                nextStats = now + (long) (STATS_INTERVAL_S * 1_000_000_000L);
            }
        }

        log.info("Final summary: {}", subscriber.getStatsSnapshot());

        // TODO: Add some analysis logic
    }
}
