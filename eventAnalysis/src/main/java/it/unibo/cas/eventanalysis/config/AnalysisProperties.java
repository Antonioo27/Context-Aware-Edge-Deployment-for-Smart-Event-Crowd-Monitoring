package it.unibo.cas.eventanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable configuration record for the Event Analysis service.
 *
 * This record maps application properties prefixed with "analysis" from application.properties
 * or Kubernetes environment variables into typed fields.
 *
 * - Broker Connectivity: Configures MQTT host, port, credentials, and session options for the local broker.
 * - Queue and Backpressure: Sets maximum queue capacity and deduplication cache size.
 * - Sliding Window Analytics: Defines window observation length (windowSize, W) and execution frequency (slideStep, S).
 * - Fail-Fast Validation: Verifies all parameters during Spring context startup to prevent runtime failures.
 */
@ConfigurationProperties(prefix = "analysis")
public record AnalysisProperties(
        String areaId,
        @DefaultValue("localhost") String mqttHost,
        @DefaultValue("1883") int mqttPort,
        @DefaultValue("30") int mqttKeepalive,
        @DefaultValue("1") int mqttQos,
        @DefaultValue("false") boolean mqttCleanSession,
        @DefaultValue("event") String mqttTopicPrefix,
        String mqttUsername,
        String mqttPassword,
        @DefaultValue("true") boolean manualAck,
        @DefaultValue("200") int maxQueueSize,
        @DefaultValue("500") int dedupWindow,
        @DefaultValue("1") int reconnectMinDelay,
        @DefaultValue("30") int reconnectMaxDelay,
        @DefaultValue("INFO") String logLevel,
        @DefaultValue("60.0") double windowSize,
        @DefaultValue("5.0") double slideStep 
) {
    public AnalysisProperties {
        if (areaId == null || areaId.isBlank()) {
            throw new IllegalArgumentException("AREA_ID is mandatory: identifies the area to analyze");
        }
        areaId = areaId.trim();

        if (areaId.contains("/") || areaId.contains("+") || areaId.contains("#")) {
            throw new IllegalArgumentException("Invalid area_id for an MQTT topic: " + areaId);
        }
        if (mqttQos < 0 || mqttQos > 2) {
            throw new IllegalArgumentException("mqttQos must be 0, 1, or 2");
        }
        if (mqttQos == 0 && !mqttCleanSession) {
            throw new IllegalArgumentException("QoS 0 makes persistent session useless");
        }
        if (mqttPort < 1 || mqttPort > 65535) {
            throw new IllegalArgumentException("mqttPort out of range");
        }
        if (mqttTopicPrefix == null || mqttTopicPrefix.isEmpty() || mqttTopicPrefix.startsWith("/") 
                || mqttTopicPrefix.endsWith("/") || mqttTopicPrefix.contains("+") || mqttTopicPrefix.contains("#")) {
            throw new IllegalArgumentException("Invalid mqttTopicPrefix");
        }
        if (maxQueueSize < 1) {
            throw new IllegalArgumentException("maxQueueSize must be >= 1");
        }
        if (dedupWindow < 1) {
            throw new IllegalArgumentException("dedupWindow must be >= 1");
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }
        if (slideStep <= 0) {
            throw new IllegalArgumentException("slideStep must be > 0");
        }
        if (slideStep > windowSize) {
            throw new IllegalArgumentException("slideStep cannot be larger than windowSize");
        }
    }

    public String topicProbes() {
        return mqttTopicPrefix + "/probes/" + areaId;
    }

    public String statusTopic() {
        return mqttTopicPrefix + "/status/analysis-" + areaId;
    }

    public String subscriberClientId() {
        return "analysis-" + areaId;
    }
}
