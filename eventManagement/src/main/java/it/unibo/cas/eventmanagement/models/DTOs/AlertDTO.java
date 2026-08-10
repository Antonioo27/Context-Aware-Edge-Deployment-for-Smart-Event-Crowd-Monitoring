package it.unibo.cas.eventmanagement.models.DTOs;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    private String area_id;
    private OffsetDateTime ts;
    private String cause;
}