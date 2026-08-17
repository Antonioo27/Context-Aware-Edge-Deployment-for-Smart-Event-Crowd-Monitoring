package it.unibo.cas.eventmanagement.models.DTOs;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ManualAlertDTO {
    private OffsetDateTime ts;
    private String cause;
    private double lon;
    private double lat;

}
