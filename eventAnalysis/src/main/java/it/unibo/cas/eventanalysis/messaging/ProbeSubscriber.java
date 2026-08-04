package it.unibo.cas.eventanalysis.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import it.unibo.cas.eventanalysis.exception.InvalidBatchException;
import it.unibo.cas.eventanalysis.models.entities.Probe;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;
import it.unibo.cas.eventanalysis.models.entities.SubscriberStats;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subscribes to an MQTT topic, deserializes batches and puts them in a
 * thread-safe queue.
 */
@Slf4j
@Component
public class ProbeSubscriber implements MqttCallbackExtended {

    private final AnalysisProperties config;
    private final ObjectMapper objectMapper;

    private final BlockingQueue<ProbeBatch> queue;

    // Dedup: LinkedHashMap to maintain order and easily remove the eldest elements
    private final Map<Long, Boolean> seenIds;

    private final Object lock = new Object();
    private final SubscriberStats stats = new SubscriberStats();
    private final CountDownLatch connectedEvent = new CountDownLatch(1);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private MqttClient client;

    public ProbeSubscriber(AnalysisProperties config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.queue = new ArrayBlockingQueue<>(config.maxQueueSize());

        final int dedupWindow = config.dedupWindow();
        this.seenIds = new LinkedHashMap<Long, Boolean>(dedupWindow, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > dedupWindow;
            }
        };

        buildClient();
    }

    private void buildClient() {
        try {
            String serverURI = "tcp://" + config.mqttHost() + ":" + config.mqttPort();
            client = new MqttClient(serverURI, config.subscriberClientId(), new MemoryPersistence());
            client.setCallback(this);
            // Python uses manual ack. Paho Java supports it via
            // MqttClient.setManualAcks(true) in MqttClient implementation (since v1.2.0)
            client.setManualAcks(config.manualAck());
        } catch (MqttException e) {
            log.error("Failed to build MQTT client", e);
            throw new RuntimeException("Failed to build MQTT client", e);
        }
    }

    public void start() {
        log.info("Starting subscriber area={} client_id={} topic={} qos={} clean_session={}",
                config.areaId(), config.subscriberClientId(), config.topicProbes(),
                config.mqttQos(), config.mqttCleanSession());

        try {
            MqttConnectOptions options = getMqttConnectOptions();

            if (!config.statusTopic().isEmpty()) {
                String payload = String.format("{\"area_id\":\"%s\",\"status\":\"offline\"}", config.areaId());
                options.setWill(config.statusTopic(), payload.getBytes(StandardCharsets.UTF_8), 1, true);
            }

            client.connect(options);
        } catch (MqttException e) {
            log.warn("Broker unreachable during startup: will keep trying in background", e);
        }
    }

    private @NonNull MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(config.mqttCleanSession());
        options.setKeepAliveInterval(config.mqttKeepalive());
        options.setAutomaticReconnect(true); // handles the reconnect min/max delay logic

        if (config.mqttUsername() != null && !config.mqttUsername().isEmpty()) {
            options.setUserName(config.mqttUsername());
            if (config.mqttPassword() != null && !config.mqttPassword().isEmpty()) {
                options.setPassword(config.mqttPassword().toCharArray());
            }
        }
        return options;
    }

    @PreDestroy
    public void stop() {
        stopping.set(true);
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.error("Error during disconnection", e);
        } finally {
            log.info("Subscriber stopped: {}", getStatsSnapshot());
        }
    }

    public boolean waitConnected(long timeout, TimeUnit unit) {
        try {
            return connectedEvent.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // MqttCallbackExtended implementation

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        synchronized (lock) {
            stats.setConnectAttempts(stats.getConnectAttempts() + 1);
        }

        // Paho doesn't easily expose session_present to connectComplete, so we just log
        // reconnect status
        log.info("Connected to broker, reconnect={}", reconnect);

        try {
            client.subscribe(config.topicProbes(), config.mqttQos());
            log.info("Subscribed to {}", config.topicProbes());
        } catch (MqttException e) {
            log.error("SUBSCRIBE failed", e);
        }

        synchronized (lock) {
            stats.setConnected(true);
        }
        connectedEvent.countDown(); // unblocks waitConnected

        if (!config.statusTopic().isEmpty()) {
            try {
                String payload = String.format("{\"area_id\":\"%s\",\"status\":\"online\"}", config.areaId());
                client.publish(config.statusTopic(), payload.getBytes(StandardCharsets.UTF_8), 1, true);
            } catch (MqttException e) {
                log.error("Failed to publish online status", e);
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        synchronized (lock) {
            stats.setConnected(false);
            if (!stopping.get()) {
                stats.setUnexpectedDisconnects(stats.getUnexpectedDisconnects() + 1);
            }
        }

        if (stopping.get()) {
            log.info("Disconnection requested by the application");
        } else {
            log.warn("Disconnected from the broker: reconnection in progress", cause);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        OffsetDateTime receivedAt = OffsetDateTime.now();
        ProbeBatch batch;
        try {
            batch = parseBatch(message.getPayload(), config.areaId(), receivedAt, message.getId(), message.getQos());
        } catch (InvalidBatchException exc) {
            synchronized (lock) {
                stats.setBatchesInvalid(stats.getBatchesInvalid() + 1);
            }
            log.warn("Batch discarded on {}: {}", topic, exc.getMessage());
            ackRaw(message.getId(), message.getQos());
            return;
        } catch (Exception exc) {
            log.error("Unexpected error parsing the message", exc);
            ackRaw(message.getId(), message.getQos());
            return;
        }

        // Duplicates handling
        boolean duplicate = false;
        synchronized (lock) {
            if (seenIds.containsKey(batch.getBatchId())) {
                stats.setBatchesDuplicated(stats.getBatchesDuplicated() + 1);
                duplicate = true;
            } else {
                seenIds.put(batch.getBatchId(), true);
            }
        }

        if (duplicate) {
            log.debug("Batch {} already seen: ignored", batch.getBatchId());
            ackRaw(message.getId(), message.getQos());
            return;
        }

        boolean added = queue.offer(batch);
        if (!added) {
            synchronized (lock) {
                stats.setBatchesDroppedFull(stats.getBatchesDroppedFull() + 1);
                seenIds.remove(batch.getBatchId());
            }
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

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used, we only subscribe
    }

    // Parsing logic

    private OffsetDateTime parseIso8601(String value) {
        if (value.endsWith("Z")) {
            value = value.substring(0, value.length() - 1) + "+00:00";
        }
        return OffsetDateTime.parse(value); // Assume fully qualified with offset
    }

    private ProbeBatch parseBatch(byte[] payload, String expectedArea, OffsetDateTime receivedAt, int mid, int qos) {
        JsonNode data;
        try {
            data = objectMapper.readTree(payload);
        } catch (Exception exc) {
            throw new InvalidBatchException("payload is not valid JSON UTF-8: " + exc.getMessage(), exc);
        }

        if (!data.isObject()) {
            throw new InvalidBatchException("payload is not a JSON object");
        }

        String[] requiredFields = { "area_id", "batch_id", "sent_at", "probes" };
        for (String field : requiredFields) {
            if (!data.has(field)) {
                throw new InvalidBatchException("missing field: " + field);
            }
        }

        String areaId = data.get("area_id").asText();
        if (expectedArea != null && !areaId.equals(expectedArea)) {
            throw new InvalidBatchException(String.format("area_id %s does not match %s", areaId, expectedArea));
        }

        long batchId;
        OffsetDateTime sentAt;
        try {
            batchId = data.get("batch_id").asLong();
            sentAt = parseIso8601(data.get("sent_at").asText());
        } catch (Exception exc) {
            throw new InvalidBatchException("invalid batch_id or sent_at field: " + exc.getMessage(), exc);
        }

        JsonNode rawProbes = data.get("probes");
        if (!rawProbes.isArray()) {
            throw new InvalidBatchException("probes field is not a list");
        }

        List<Probe> probes = new ArrayList<>();
        int malformedProbes = 0;
        ArrayNode probesArray = (ArrayNode) rawProbes;

        for (JsonNode item : probesArray) {
            try {
                String sensorId = item.get("sensor_id").asText();
                OffsetDateTime ts = parseIso8601(item.get("ts").asText());
                String mac = item.get("mac").asText();
                int rssi = item.get("rssi").asInt();
                probes.add(new Probe(sensorId, ts, mac, rssi));
            } catch (Exception exc) {
                malformedProbes++;
            }
        }

        int countDeclared = data.has("count") ? data.get("count").asInt() : probes.size();

        return ProbeBatch.builder()
                .areaId(areaId)
                .batchId(batchId)
                .sentAt(sentAt)
                .receivedAt(receivedAt)
                .countDeclared(countDeclared)
                .probes(probes)
                .malformedProbes(malformedProbes)
                .mid(mid)
                .qos(qos)
                .build();
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
        ackRaw(batch.getMid(), batch.getQos());
    }

    private void ackRaw(int mid, int qos) {
        if (!config.manualAck() || qos == 0) {
            return;
        }
        try {
            if (client != null && client.isConnected()) {
                client.messageArrivedComplete(mid, qos);
            }
        } catch (MqttException e) {
            log.error("Ack of message mid={} failed", mid, e);
        }
    }

    // Diagnostics

    public boolean isConnected() {
        synchronized (lock) {
            return stats.isConnected();
        }
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
            s.put("connected", stats.isConnected());
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
        return stopping.get();
    }
}
