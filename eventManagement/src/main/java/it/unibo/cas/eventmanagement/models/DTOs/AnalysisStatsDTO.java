package it.unibo.cas.eventmanagement.models.DTOs;

import it.unibo.cas.eventmanagement.models.enums.Trend;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AnalysisStatsDTO {
    private String area_id;
    private OffsetDateTime ts;
    private Integer window_seconds;
    private Long estimatedPeople;
    private Trend trend;
    private String served_by;
    private Double density;
    private String node;

}
