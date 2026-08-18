package it.unibo.cas.eventmanagement.models.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionPointDTO {
    private OffsetDateTime ts;
    private double estimatedPeople;
}
