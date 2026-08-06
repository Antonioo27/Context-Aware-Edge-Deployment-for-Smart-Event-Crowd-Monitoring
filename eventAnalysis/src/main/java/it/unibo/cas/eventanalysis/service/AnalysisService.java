package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.models.entities.Area;
import it.unibo.cas.eventanalysis.models.entities.Probe;
import it.unibo.cas.eventanalysis.models.entities.ProbeBatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class AnalysisService {
    @Value("${eventanalysis.analysis.windows-size:60}")
    private int windowSize;

    @Value("${eventanalysis.analysis.mean-exp-int:25}")
    private int mean;

    @Autowired
    private Area area;

    public int countNumDifferentMac(List<ProbeBatch> probeBatches) {
        HashMap<String, Probe> probeList = new HashMap<>();
        for (ProbeBatch probeBatch : probeBatches) {
            for (Probe probe : probeBatch.getProbes())
                if (!probeList.containsKey(probe.mac()))
                    probeList.put(probe.mac(), probe);
        }
        return probeList.size();
    }

    public long estimatePeople(List<ProbeBatch> probeBatches){
        return Math.round(
                        countNumDifferentMac(probeBatches) / (1 - Math.exp(((double) -windowSize / mean))));
    }

    public double density(long estimatedPeople) {
        return estimatedPeople / area.m2();
    }

    public double density(List<ProbeBatch> probeBatches) {
        return estimatePeople(probeBatches) / area.m2();
    }

    public void trend(){
        // TODO
    }

}
