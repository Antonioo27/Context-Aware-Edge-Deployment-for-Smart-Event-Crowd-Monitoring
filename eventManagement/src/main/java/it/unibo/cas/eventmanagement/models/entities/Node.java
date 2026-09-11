package it.unibo.cas.eventmanagement.models.entities;

import it.unibo.cas.eventmanagement.models.enums.NodeType;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {

    @Id
    private String id; 

    private String name;

    @Enumerated(EnumType.STRING)
    private NodeType type; 

    @Column(name = "broker_url", nullable = false)
    private String brokerUrl; 

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location; 
}