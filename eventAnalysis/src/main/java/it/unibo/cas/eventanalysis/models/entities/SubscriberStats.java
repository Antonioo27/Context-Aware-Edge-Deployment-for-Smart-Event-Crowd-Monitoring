package it.unibo.cas.eventanalysis.models.entities;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SubscriberStats {
    private boolean connected = false;
    private int connectAttempts = 0;
    private int unexpectedDisconnects = 0;
    private int batchesReceived = 0;
    private int batchesInvalid = 0;
    private int batchesDuplicated = 0;
    private int batchesDroppedFull = 0;
    private int probesReceived = 0;
    private int probesMalformed = 0;
    private OffsetDateTime lastBatchAt = null;
    private Double lastLatencyMs = null;
    
    private double latencySumMs = 0.0;

    public Double getAvgLatencyMs() {
        if (batchesReceived == 0) {
            return null;
        }
        return latencySumMs / batchesReceived;
    }
}
