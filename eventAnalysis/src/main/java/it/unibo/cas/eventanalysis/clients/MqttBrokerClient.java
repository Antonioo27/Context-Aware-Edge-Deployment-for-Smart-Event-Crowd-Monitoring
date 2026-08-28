package it.unibo.cas.eventanalysis.clients;

import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import jakarta.annotation.PreDestroy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class MqttBrokerClient implements MqttCallbackExtended {

    private final AnalysisProperties config;
    private final MqttAsyncClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    // Inizializzazione ObjectMapper con supporto a OffsetDateTime (ISO-8601)
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Setter
    private MqttConnectionListener connectionListener;
    @Setter
    private MqttMessageListener messageListener;

    public interface MqttConnectionListener {
        void onConnectComplete(boolean reconnect);
        void onConnectionLost(Throwable cause);
    }

    public interface MqttMessageListener {
        void onMessageArrived(String topic, int mid, int qos, byte[] payload);
    }

    public MqttBrokerClient(AnalysisProperties config) {
        this.config = config;
        try {
            String serverURI = "tcp://" + config.mqttHost() + ":" + config.mqttPort();
            client = new MqttAsyncClient(serverURI, config.subscriberClientId(), new MemoryPersistence());
            client.setCallback(this);
            client.setManualAcks(config.manualAck());
        } catch (MqttException e) {
            log.error("Failed to build MQTT client", e);
            throw new RuntimeException("Failed to build MQTT client", e);
        }
    }

    public void connect() {
        if (stopping.get()) return;

        try {
            MqttConnectOptions options = getMqttConnectOptions();

            if (!config.statusTopic().isEmpty()) {
                String payload = String.format("{\"area_id\":\"%s\",\"status\":\"offline\"}", config.areaId());
                options.setWill(config.statusTopic(), payload.getBytes(StandardCharsets.UTF_8), 1, true);
            }

            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log.debug("Initial async connect call succeeded.");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (!stopping.get()) {
                        log.warn("Broker unreachable during startup. Retrying in 5 seconds...");
                        scheduler.schedule(() -> connect(), 5, TimeUnit.SECONDS);
                    }
                }
            });
        } catch (MqttException e) {
            if (!stopping.get()) {
                log.warn("Failed to initiate connection. Retrying in 5 seconds...", e);
                scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
            }
        }
    }

    private @NonNull MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(config.mqttCleanSession());
        options.setKeepAliveInterval(config.mqttKeepalive());
        options.setAutomaticReconnect(true);

        if (config.mqttUsername() != null && !config.mqttUsername().isEmpty()) {
            options.setUserName(config.mqttUsername());
            if (config.mqttPassword() != null && !config.mqttPassword().isEmpty()) {
                options.setPassword(config.mqttPassword().toCharArray());
            }
        }
        return options;
    }

    public void subscribe(String topic, int qos, IMqttActionListener listener) {
        try {
            client.subscribe(topic, qos, null, listener);
        } catch (MqttException e) {
            log.error("Failed to initiate SUBSCRIBE", e);
        }
    }

    public void publish(String topic, byte[] payload, int qos, boolean retained) throws MqttException {
        client.publish(topic, payload, qos, retained, null, null);
    }
    
    public void publishAlert(String topic, Object payload) {
        try {
            if (isConnected()) {
                String json = objectMapper.writeValueAsString(payload);
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                
                // Sfrutta il metodo publish già esistente
                this.publish(topic, bytes, 1, false);
                log.info("[FAST-PATH] Alert pubblicato su MQTT topic: {}", topic);
            } else {
                log.warn("Impossibile inviare alert Fast-Path: client MQTT non connesso");
            }
        } catch (Exception e) {
            log.error("Errore durante la pubblicazione MQTT dell'alert: {}", e.getMessage());
        }
    }

    public void ack(int mid, int qos) {
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

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public boolean isStopping() {
        return stopping.get();
    }

    @PreDestroy
    public void disconnect() {
        stopping.set(true);
        scheduler.shutdownNow();
        try {
            if (client != null && client.isConnected()) {
                client.disconnect().waitForCompletion(5000);
            }
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.error("Error during disconnection", e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (connectionListener != null) {
            connectionListener.onConnectComplete(reconnect);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        if (connectionListener != null) {
            connectionListener.onConnectionLost(cause);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if (messageListener != null) {
            messageListener.onMessageArrived(topic, message.getId(), message.getQos(), message.getPayload());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used
    }
}
