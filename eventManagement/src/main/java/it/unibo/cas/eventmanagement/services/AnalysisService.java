package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import it.unibo.cas.eventmanagement.models.enums.State;
import it.unibo.cas.eventmanagement.repositories.AnalysisStatsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AnalysisService {

    @Value("${eventmanagement.analysis.critical-threshold:0.8}")
    private double criticalThreshold;

    @Value("${eventmanagement.analysis.high-threshold:0.6}")
    private double highThreshold;

    @Value("${eventmanagement.analysis.medium-threshold:0.4}")
    private double mediumThreshold;

    @Autowired
    private AnalysisStatsRepository analysisStatsRepository;

    @Autowired
    private AreaService areaService;

    /**
     * Adds a new analysis to the repository.
     * 
     * @param analysisStats the analysis to add
     * @return the added analysis
     */
    public AnalysisStats addAnalysis(AnalysisStats analysisStats) {
        if (analysisStats == null) {
            throw new IllegalArgumentException("Area statistical analysis is null");
        }
        AnalysisStats as = analysisStatsRepository.save(analysisStats);
        log.info("Added analysis stats to analysis: {}", analysisStats);

        setAreaState(analysisStats.getEstimatedPeople(), analysisStats.getAreaId());
        return as;
    }

    /**
     * Sets the state of an area based on the estimated number of people.
     * 
     * @param estimatedPeople the estimated number of people
     * @param areaId          the ID of the area
     */
    private void setAreaState(long estimatedPeople, String areaId) {
        int maxCapacity = areaService.getCapacity(areaId);
        if ((double) estimatedPeople / maxCapacity > criticalThreshold) {
            areaService.setAreaState(State.CRITICAL, areaId);
        } else if ((double) estimatedPeople / maxCapacity > highThreshold) {
            areaService.setAreaState(State.HIGH, areaId);
        } else if ((double) estimatedPeople / maxCapacity > mediumThreshold) {
            areaService.setAreaState(State.MEDIUM, areaId);
        } else {
            areaService.setAreaState(State.LOW, areaId);
        }
    }
}