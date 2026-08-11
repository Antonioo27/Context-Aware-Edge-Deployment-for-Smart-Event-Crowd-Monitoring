package it.unibo.cas.eventanalysis.messaging;

import it.unibo.cas.eventanalysis.clients.MqttBrokerClient;
import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import it.unibo.cas.eventanalysis.exception.InvalidBatchException;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;
import it.unibo.cas.eventanalysis.models.entities.SubscriberStats;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Subscribes to an MQTT topic on the LOCAL broker, delegates deserialization to
 * ProbeBatchParser,
 * handles deduplication, and puts batches in a thread-safe queue.
 */
@Slf4j
@Component
public class ProbeSubscriber implements MqttBrokerClient.MqttConnectionListener, MqttBrokerClient.MqttMessageListener {

    private final AnalysisProperties config;
    private final MqttBrokerClient mqttClient;
    private final ProbeBatchParser batchParser;
    private final MessageDeduplicator deduplicator;

    private final BlockingQueue<ProbeBatch> queue;
    private final Object lock = new Object();
    private final SubscriberStats stats = new SubscriberStats();
    private final CountDownLatch connectedEvent = new CountDownLatch(1);

    public ProbeSubscriber(AnalysisProperties config, MqttBrokerClient mqttClient, ProbeBatchParser batchParser) {
        this.config = config;
        this.mqttClient = mqttClient;
        this.batchParser = batchParser;
        this.deduplicator = new MessageDeduplicator(config.dedupWindow());
        this.queue = new ArrayBlockingQueue<>(config.maxQueueSize());

        // Register as listener
        this.mqttClient.setConnectionListener(this);
        this.mqttClient.setMessageListener(this);
    }

    public void start() {
        log.info("Starting subscriber area={} client_id={} topic={} qos={} clean_session={}",
                config.areaId(), config.subscriberClientId(), config.topicProbes(),
                config.mqttQos(), config.mqttCleanSession());

        mqttClient.connect();
    }

    public boolean waitConnected(long timeout, TimeUnit unit) {
        try {
            return connectedEvent.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    public void stop() {
        log.info("ProbeSubscriber stop by area={}...", config.areaId());
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (Exception e) {
            log.warn("Error during the disconnection MQTT: {}", e.getMessage());
        }
    }

    // Consumer API

    public ProbeBatch get(long timeout, TimeUnit unit) {
        try {
            return queue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void ack(ProbeBatch batch) {
        mqttClient.ack(batch.getMid(), batch.getQos());
    }

    // Diagnostics

    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    public int getPending() {
        return queue.size();
    }

    public boolean isHealthy(double maxSilenceS) {
        if (!isConnected()) {
            return false;
        }
        OffsetDateTime last;
        synchronized (lock) {
            last = stats.getLastBatchAt();
        }
        if (last == null) {
            return true; // just started
        }
        double silenceS = java.time.Duration.between(last, OffsetDateTime.now()).toNanos() / 1_000_000_000.0;
        return silenceS <= maxSilenceS;
    }

    public Map<String, Object> getStatsSnapshot() {
        synchronized (lock) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("area_id", config.areaId());
            s.put("client_id", config.subscriberClientId());
            s.put("topic", config.topicProbes());
            s.put("connected", isConnected());
            s.put("connect_attempts", stats.getConnectAttempts());
            s.put("unexpected_disconnects", stats.getUnexpectedDisconnects());
            s.put("batches_received", stats.getBatchesReceived());
            s.put("batches_invalid", stats.getBatchesInvalid());
            s.put("batches_duplicated", stats.getBatchesDuplicated());
            s.put("batches_dropped_full", stats.getBatchesDroppedFull());
            s.put("probes_received", stats.getProbesReceived());
            s.put("probes_malformed", stats.getProbesMalformed());
            s.put("queue_size", queue.size());
            s.put("last_batch_at", stats.getLastBatchAt() != null ? stats.getLastBatchAt().toString() : null);
            s.put("last_latency_ms", stats.getLastLatencyMs());
            s.put("avg_latency_ms", stats.getAvgLatencyMs());
            return s;
        }
    }

    public boolean isStopping() {
        return mqttClient.isStopping();
    }

    // Callbacks from MqttBrokerClient

    @Override
    public void onConnectComplete(boolean reconnect) {
        synchronized (lock) {
            stats.setConnectAttempts(stats.getConnectAttempts() + 1);
        }

        log.info("Connected to broker, reconnect={}", reconnect);

        mqttClient.subscribe(config.topicProbes(), config.mqttQos(), new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                log.info("Subscribed to {}", config.topicProbes());
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                log.error("SUBSCRIBE failed", exception);
            }
        });

        synchronized (lock) {
            stats.setConnected(true);
        }
        connectedEvent.countDown(); // unblocks waitConnected
    }

    @Override
    public void onConnectionLost(Throwable cause) {
        synchronized (lock) {
            stats.setConnected(false);
            if (!mqttClient.isStopping()) {
                stats.setUnexpectedDisconnects(stats.getUnexpectedDisconnects() + 1);
            }
        }

        if (mqttClient.isStopping()) {
            log.info("Disconnection requested by the application");
        } else {
            log.warn("Disconnected from the broker: reconnection in progress", cause);
        }
    }

    @Override
    public void onMessageArrived(String topic, int mid, int qos, byte[] payload) {
        OffsetDateTime receivedAt = OffsetDateTime.now();
        ProbeBatch batch;
        
        try {
            batch = batchParser.parseBatch(payload, config.areaId(), receivedAt, mid, qos);
        } catch (InvalidBatchException exc) {
            synchronized (lock) {
                stats.setBatchesInvalid(stats.getBatchesInvalid() + 1);
            }
            log.warn("Batch discarded on {}: {}", topic, exc.getMessage());
            mqttClient.ack(mid, qos);
            return;
        } catch (Exception exc) {
            log.error("Unexpected error parsing the message", exc);
            mqttClient.ack(mid, qos);
            return;
        }

        // Duplicates handling
        if (deduplicator.isDuplicateOrAdd(batch.getBatchId())) {
            synchronized (lock) {
                stats.setBatchesDuplicated(stats.getBatchesDuplicated() + 1);
            }
            log.debug("Batch {} already seen: ignored", batch.getBatchId());
            mqttClient.ack(mid, qos);
            return;
        }

        boolean added = queue.offer(batch);
        if (!added) {
            synchronized (lock) {
                stats.setBatchesDroppedFull(stats.getBatchesDroppedFull() + 1);
            }
            deduplicator.remove(batch.getBatchId());
            log.error("Queue full ({}): batch {} not enqueued, consumer is too slow", config.maxQueueSize(),
                    batch.getBatchId());
            return;
        }

        synchronized (lock) {
            stats.setBatchesReceived(stats.getBatchesReceived() + 1);
            stats.setProbesReceived(stats.getProbesReceived() + batch.getCountEffective());
            stats.setProbesMalformed(stats.getProbesMalformed() + batch.getMalformedProbes());
            stats.setLastBatchAt(receivedAt);
            stats.setLastLatencyMs(batch.getTransportLatencyMs());
            stats.setLatencySumMs(stats.getLatencySumMs() + batch.getTransportLatencyMs());
        }
    }
}
