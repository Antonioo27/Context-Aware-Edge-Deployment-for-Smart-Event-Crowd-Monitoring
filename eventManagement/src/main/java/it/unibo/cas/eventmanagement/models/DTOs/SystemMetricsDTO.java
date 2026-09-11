package it.unibo.cas.eventmanagement.models.DTOs;

import java.util.Map;

public record SystemMetricsDTO(
    long totalRequestsProcessed,
    double instantAverageLatencyMs,     // Slow-Path: L_totale per sincronizzazione DB
    double instantFastPathLatencyMs,    // Fast-Path: Reattività immediata allarmi
    long totalAlertsCount,
    Map<String, Double> areaLatencies,
    Map<String, Double> areaFastPathLatencies,
    Map<String, Double> nodeCpuLoad,
    Map<String, Integer> nodePodCount
) {}