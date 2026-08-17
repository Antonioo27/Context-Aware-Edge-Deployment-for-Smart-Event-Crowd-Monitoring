package it.unibo.cas.eventmanagement.models.entities;

import it.unibo.cas.eventmanagement.models.enums.Trend;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "analysis_stats")
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AnalysisStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long id;

    @Column(name = "area_name")
    private String areaId;

    private OffsetDateTime ts;

    @Column(name = "window_seconds")
    private int windowSeconds;

    @Column(name = "estimated_people")
    private long estimatedPeople;

    private Trend trend;

    @Column(name = "served_by")
    private String servedBy;

    private double density;
    private String node;
}