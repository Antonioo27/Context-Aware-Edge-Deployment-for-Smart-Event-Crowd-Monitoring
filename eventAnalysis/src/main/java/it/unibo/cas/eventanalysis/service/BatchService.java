package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.models.entities.Probe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;

import java.util.ArrayList;


@Service
public class BatchService {
    @Value("${eventanalysis.probe.threshold-rssi:-100}")
    private int thresholdRssi;

    /**
     * Check if the batch is the last one.
     * 
     * @param batch     the batch to check
     * @param lastBatch the last batch (can be null)
     * @return true if the batch is the consecutive one, false otherwise
     */
    public boolean batchIsLast(ProbeBatch batch, ProbeBatch lastBatch) {
        if (lastBatch == null) {
            return true; // if there is no last batch, it's the first one, so sequence is fine
        }
        return batch.getBatchId() == (lastBatch.getBatchId() + 1);
    }

    /**
     * Filter probes in the batch. Remove probes with rssi() < thresholdRssi.
     * 
     * @param batch the batch to filter
     * @return the batch filtered
     */
    public ProbeBatch filterRSSI(ProbeBatch batch) {
        ArrayList<Probe> probes = new ArrayList<>();
        for (Probe probe : batch.getProbes()) {
            if (probe.rssi() > thresholdRssi)
                probes.add(probe);
        }
        batch.setProbes(probes);
        return batch;
    }
}
