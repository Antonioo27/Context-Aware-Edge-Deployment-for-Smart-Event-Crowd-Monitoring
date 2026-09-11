package it.unibo.cas.eventmanagement.models.DTOs;

import java.util.Map;

public record SystemMetricsDTO(
    long totalRequestsProcessed,
    double instantAverageLatencyMs,    
    double instantFastPathLatencyMs,   
    long totalAlertsCount,
    Map<String, Double> areaLatencies,
    Map<String, Double> areaFastPathLatencies,
    Map<String, Double> nodeCpuLoad,
    Map<String, Integer> nodePodCount
) {}