package it.unibo.cas.eventmanagement.models.DTOs;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationDTO {
    private Long id;
    private String areaId;
    private String podName;
    private String fromNode;
    private String toNode;
    private Double previousCost;
    private Double newCost;
    private String reason;
    private Boolean success;
    private String errorMessage;
    private OffsetDateTime timestamp;
}