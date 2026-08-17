package it.unibo.cas.eventmanagement.models.entities;


import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "alerts")
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Alert{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long id;
    private String areaId;
    private OffsetDateTime ts;
    private String cause;
}
