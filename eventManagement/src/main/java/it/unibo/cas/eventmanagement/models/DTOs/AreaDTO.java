package it.unibo.cas.eventmanagement.models.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.unibo.cas.eventmanagement.models.enums.AreaType;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.models.enums.State;
import it.unibo.cas.eventmanagement.utils.PolygonDeserializer;
import it.unibo.cas.eventmanagement.utils.PolygonSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import org.locationtech.jts.geom.Polygon;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AreaDTO {
    private String name;
    private Integer capacity;
    private Priority priority;
    private AreaType type;
    private State state;
    
    @JsonSerialize(using = PolygonSerializer.class)
    @JsonDeserialize(using = PolygonDeserializer.class)
    private Polygon boundary;
}
