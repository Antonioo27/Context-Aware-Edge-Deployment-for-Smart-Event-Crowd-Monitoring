package it.unibo.cas.eventmanagement.models.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDistanceDTO {
    private String areaName;
    private String nodeId;
    private double distanceMeters;
    private double estimatedIngressLatencyMs;
}