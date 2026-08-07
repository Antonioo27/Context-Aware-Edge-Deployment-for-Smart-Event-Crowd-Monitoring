package it.unibo.cas.eventmanagement.models.DTOs;

import it.unibo.cas.eventmanagement.models.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDTO {
    private String id;
    private String name;
    private NodeType type;
    private String brokerUrl;
    private double latitude;
    private double longitude;
}