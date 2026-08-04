package it.unibo.cas.eventanalysis.models.entities;

import java.time.OffsetDateTime;

/**
 * A single WiFi probe.
 */
public record Probe(
        String sensorId,
        OffsetDateTime ts,
        String mac,
        int rssi
) {}
