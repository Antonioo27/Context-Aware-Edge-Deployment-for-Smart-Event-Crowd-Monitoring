package it.unibo.cas.eventanalysis.models.entities;

import it.unibo.cas.eventanalysis.models.enums.Trend;
import lombok.*;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStats {
    private long estimatedPeople;
    private double density;
    private double latency;
    @Setter
    private Trend trend;
}
