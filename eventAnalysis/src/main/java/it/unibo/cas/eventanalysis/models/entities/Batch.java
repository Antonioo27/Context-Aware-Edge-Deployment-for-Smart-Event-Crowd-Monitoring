package it.unibo.cas.eventanalysis.models.entities;

import lombok.*;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Batch {
    private int batch_id;
    private String area_id;
    private Date sent_at;
    private int count;
    private List<Probe> probes;
}
