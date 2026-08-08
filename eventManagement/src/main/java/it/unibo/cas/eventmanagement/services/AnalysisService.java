package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AnalysisService {
    @Autowired
    private AreaService areaService;


    public void addAnalysis(AnalysisStats analysisStats) {
        // TODO
        log.debug("Adding analysis stats to analysis: {}", analysisStats);
    }
}
