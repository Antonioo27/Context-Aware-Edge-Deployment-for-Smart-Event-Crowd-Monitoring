package it.unibo.cas.eventmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "eventmanagement.latency")
public class LatencyProperties {

    // Tratte di rete base
    private double transportMs = 1.0;
    private double exitEdgeMs = 40.0;
    private double exitCloudMs = 1.0;
    private double cloudIngressMs = 40.0;
    private double baseEdgeMs = 1.0;
    private double msPerMeter = 4.0 / 100.0; // 3ms ogni 100 metri

    // Tratte Fast-Path (Notifiche Alert)
    private double wsLocalMs = 1.5;
    private double wsWanMs = 40.0;

    // Smoothing Metriche
    private double emaAlpha = 0.2;
}