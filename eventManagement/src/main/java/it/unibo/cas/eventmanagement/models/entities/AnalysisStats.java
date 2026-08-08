package it.unibo.cas.eventmanagement.models.entities;

import it.unibo.cas.eventmanagement.models.enums.Trend;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.OffsetDateTime;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AnalysisStats {
    private String area_id;
    private OffsetDateTime ts;
    private int window_seconds;
    private long estimatedPeople;
    private Trend trend;
    private String served_by;
    private double density;
    private String node;

}
