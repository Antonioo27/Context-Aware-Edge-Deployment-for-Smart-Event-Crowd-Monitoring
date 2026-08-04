package it.unibo.cas.eventmanagement.models.entities;

import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.models.enums.State;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Polygon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import it.unibo.cas.eventmanagement.utils.PolygonDeserializer;
import it.unibo.cas.eventmanagement.utils.PolygonSerializer;

@Entity
@Table(name = "areas")
@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Area {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "area_id")
    private int id;

    @Column(unique=true)
    private String name;
    private int capacity;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private State state = State.NONE;

    @Column(columnDefinition = "geometry(Polygon,4326)")
    @JsonSerialize(using = PolygonSerializer.class)
    @JsonDeserialize(using = PolygonDeserializer.class)
    private Polygon boundary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id") // Questa è la Foreign Key nella tabella areas
    @JsonIgnore
    private Event event;
}
