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
    private String id; // es. "node-edge-north", "node-edge-south", "node-cloud"

    private String name;

    @Enumerated(EnumType.STRING)
    private NodeType type; // EDGE oppure CLOUD

    @Column(name = "broker_url", nullable = false)
    private String brokerUrl; // es. "tcp://mosquitto-north:1883"

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location; // Posizione geospaziale del nodo (Lat, Lon)
}