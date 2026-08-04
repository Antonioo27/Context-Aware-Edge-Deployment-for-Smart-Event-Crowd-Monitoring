package it.unibo.cas.eventanalysis.models.entities;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A batch of probes for a single area, already validated.
 */
@Data
@Builder
public class ProbeBatch {
    private String areaId;
    private long batchId;
    private OffsetDateTime sentAt;       // when the simulator published (UTC)
    private OffsetDateTime receivedAt;   // when we received (UTC)
    private int countDeclared;           // "count" field declared in the message
    private List<Probe> probes;
    private int malformedProbes;         // discarded probes

    // MQTT Metadata: for manual ack.
    @Builder.Default
    private int mid = 0;
    
    @Builder.Default
    private int qos = 1;

    /**
     * Broker->service latency in milliseconds (requires synchronized clocks).
     */
    public double getTransportLatencyMs() {
        if (receivedAt != null && sentAt != null) {
            return Duration.between(sentAt, receivedAt).toNanos() / 1_000_000.0;
        }
        return 0.0;
    }

    /**
     * How many probes are actually usable after parsing.
     */
    public int getCountEffective() {
        return probes != null ? probes.size() : 0;
    }
}
