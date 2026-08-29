package it.unibo.cas.eventmanagement.models.DTOs;

import it.unibo.cas.eventmanagement.models.enums.OrchestrationPolicy;
import java.util.Map;

public record SystemMetricsDTO(
    long totalRequestsProcessed,
    double instantAverageLatencyMs,     // Slow-Path: L_totale per sincronizzazione DB
    double emaAverageLatencyMs,         // Slow-Path filtrato EMA
    double instantFastPathLatencyMs,    // Fast-Path: Reattività immediata allarmi
    double emaFastPathLatencyMs,        // Fast-Path filtrato EMA
    long totalAlertsCount,
    Map<String, Double> areaLatencies,
    Map<String, Double> areaFastPathLatencies,
    Map<String, Double> nodeCpuLoad,
    Map<String, Integer> nodePodCount
) {}