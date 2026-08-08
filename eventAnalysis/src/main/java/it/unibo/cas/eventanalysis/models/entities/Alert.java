package it.unibo.cas.eventanalysis.models.entities;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    private String area_id;
    private OffsetDateTime ts;
    private String cause;
}