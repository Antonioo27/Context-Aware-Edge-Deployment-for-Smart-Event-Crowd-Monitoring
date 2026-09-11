package it.unibo.cas.eventmanagement.models.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "migrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Migration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String areaId;

    @Column(nullable = false)
    private String podName;

    @Column(nullable = false)
    private String fromNode;

    @Column(nullable = false)
    private String toNode;

    private Double previousCost;

    private Double newCost;

    @Column(nullable = false, length = 500)
    private String reason; 

    @Column(nullable = false)
    private Boolean success;

    private String errorMessage;

    @Column(nullable = false)
    private OffsetDateTime timestamp;
}