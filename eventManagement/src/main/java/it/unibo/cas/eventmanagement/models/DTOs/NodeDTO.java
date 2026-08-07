package it.unibo.cas.eventmanagement.models.DTOs;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("id")
    private String id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("type")
    private NodeType type;
    @JsonProperty("brokerUrl")
    private String brokerUrl;
    @JsonProperty("latitude")
    private double latitude;
    @JsonProperty("longitude")
    private double longitude;
}