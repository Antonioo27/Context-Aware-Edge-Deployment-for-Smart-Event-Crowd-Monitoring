package it.unibo.cas.eventanalysis.messaging;

import it.unibo.cas.eventanalysis.models.entities.Area;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * A subscriber to MQTT messages.
 */
@Component
public class ProbeDataSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(ProbeDataSubscriber.class);

    private final Area area;

    public ProbeDataSubscriber(Area area) {
        this.area = area;
    }

    /**
     * Receives incoming messages from Mosquitto through the mqttInputChannel
     * defined in MqttConfiguration.
     * 
     * @param message The incoming message
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void onMessageReceived(Message<String> message) {
        String payload = message.getPayload();
        String receivedTopic = message.getHeaders().get("mqtt_receivedTopic", String.class);

        logger.info("[{}] - Received new data for the area [{}]: {}",
                receivedTopic != null ? receivedTopic : "unknown_topic", area.id(), payload);

        // TODO: Delegate the logic to a "CrowdAnalysisService"
        // processPayload(payload);
    }
}