package it.unibo.cas.eventanalysis.config;

import it.unibo.cas.eventanalysis.models.entities.Area;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

import java.util.UUID;

@Configuration
public class MqttConfiguration {

    @Value("${MQTT_BROKER_URL:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${MQTT_USERNAME:}")
    private String username;

    @Value("${MQTT_PASSWORD:}")
    private String password;

    /**
     * MQTT (Paho) Client Factory Configuration
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[] { brokerUrl });
        if (!username.isEmpty()) {
            options.setUserName(username);
        }
        if (!password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * The internal Spring channel where incoming messages will travel
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * The MessageProducer that listens to Mosquitto and forwards data to the
     * internal channel.
     * 
     * @param mqttClientFactory The MQTT client factory
     * @param currentArea       The current area
     * @return The MessageProducer
     */
    @Bean
    public MessageProducer inbound(MqttPahoClientFactory mqttClientFactory, Area currentArea) {
        // Generate a unique client ID to allow scalability
        String clientId = "event-analysis-" + currentArea.id() + "-" + UUID.randomUUID().toString();

        // Construct the topic (e.g., "event/probes/areaID")
        String topic = "event/probes/" + currentArea.id();

        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(clientId,
                mqttClientFactory, topic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());

        return adapter;
    }
}
