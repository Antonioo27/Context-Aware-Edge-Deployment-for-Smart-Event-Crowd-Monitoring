package it.unibo.cas.eventmanagement.models.DTOs;

import it.unibo.cas.eventmanagement.models.enums.AlertType;
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
    private AlertType alertType = AlertType.AUTOMATIC;
}