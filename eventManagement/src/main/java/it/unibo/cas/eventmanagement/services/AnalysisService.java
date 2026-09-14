package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.PredictionPointDTO;
import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import it.unibo.cas.eventmanagement.models.enums.AlertType;
import it.unibo.cas.eventmanagement.models.enums.State;
import it.unibo.cas.eventmanagement.repositories.AnalysisStatsRepository;
import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.utils.LinearRegressionPredictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

/**
 * Service responsible for processing crowd analysis statistics, managing area
 * congestion states,
 * generating proactive prediction-based alerts, and delivering future
 * prediction trends.
 */
@Service
@Slf4j
public class AnalysisService {

    @Value("${eventmanagement.analysis.critical-threshold:0.8}")
    private double criticalThreshold;

    @Value("${eventmanagement.analysis.high-threshold:0.6}")
    private double highThreshold;

    @Value("${eventmanagement.analysis.medium-threshold:0.4}")
    private double mediumThreshold;

    @Value("${eventmanagement.analysis.prediction.horizon-minutes:2}")
    private int predictionHorizonMinutes;

    @Value("${eventmanagement.analysis.prediction.window-minutes:2}")
    private int predictionWindowMinutes;

    @Value("${eventmanagement.analysis.prediction.default-step-size:15}")
    private int defaultStepSize;

    @Autowired
    private AnalysisStatsRepository analysisStatsRepository;

    @Autowired
    private AreaService areaService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotifyService notifyService;

    private final Map<String, OffsetDateTime> lastPredictionAlerts = new ConcurrentHashMap<>();

    /**
     * Adds a new analysis to the repository and triggers proactive crowd
     * evaluation.
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
        checkPrediction(as);

        return as;
    }

    /**
     * Executes crowd prediction for the incoming statistics and emits proactive
     * alerts when critical thresholds are exceeded.
     * 
     * @param latestStats latest recorded analysis statistics
     */
    private void checkPrediction(AnalysisStats latestStats) {
        if (latestStats.getTs() == null) {
            return;
        }

        OffsetDateTime windowStart = latestStats.getTs().minusMinutes(predictionWindowMinutes);
        List<AnalysisStats> history = analysisStatsRepository.findRecentByAreaId(latestStats.getAreaId(), windowStart);

        int capacity = areaService.getCapacity(latestStats.getAreaId());
        Double predictedPeople = LinearRegressionPredictor.predictFutureCrowd(history, predictionHorizonMinutes,
                capacity);

        if (predictedPeople != null) {
            log.info("Predicted people in area {} in {} minutes: {}", latestStats.getAreaId(), predictionHorizonMinutes,
                    predictedPeople.intValue());

            double predictedOccupancy = predictedPeople / capacity;

            if (predictedOccupancy > criticalThreshold) {
                OffsetDateTime lastAlert = lastPredictionAlerts.get(latestStats.getAreaId());
                if (lastAlert == null || OffsetDateTime.now().isAfter(lastAlert.plusMinutes(2))) {

                    log.warn("PREDICTION ALERT: Area {} will become CRITICAL in {} minutes!", latestStats.getAreaId(),
                            predictionHorizonMinutes);

                    AlertDTO alertDTO = AlertDTO.builder()
                            .area_id(latestStats.getAreaId())
                            .ts(OffsetDateTime.now())
                            .alertType(AlertType.PREDICTION)
                            .cause(String.format(
                                    "Predizione: l'area supererà la soglia critica tra %d minuti. (Stima: %d persone)",
                                    predictionHorizonMinutes, predictedPeople.intValue()))
                            .build();

                    Alert alert = alertService.addAlert(alertDTO);
                    notifyService.notifyAutomaticAlert(alert);
                    lastPredictionAlerts.put(latestStats.getAreaId(), OffsetDateTime.now());
                } else {
                    log.debug("Prediction alert for area {} skipped (cooldown active)", latestStats.getAreaId());
                }
            }
        }
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

    /**
     * Gets the statistical analysis of an area.
     * 
     * @param areaId the ID of the area
     * @param type   the type of the analysis
     * @return the statistical analysis of an area
     * @param <T> the type of the analysis
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getStatsByArea(String areaId, Class<T> type) {
        if (areaId == null) {
            throw new IllegalArgumentException("Area id is null");
        }

        if (type == Long.class) {
            return (List<T>) analysisStatsRepository.findEstimatedPeopleByAreaIdOrderByTs(areaId);
        } else if (type == Double.class) {
            return (List<T>) analysisStatsRepository.findDensityByAreaIdOrderByTs(areaId);
        } else if (type == AnalysisStats.class) {
            return (List<T>) analysisStatsRepository.findAllByAreaIdOrderByTs(areaId);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getSimpleName());
        }
    }

    /**
     * Computes and returns the list of predicted future trend points for the
     * frontend.
     * 
     * @param areaId area identifier
     * @return list of prediction points containing timestamps and estimated crowd
     *         sizes
     */
    public List<PredictionPointDTO> getPredictionTrendByArea(String areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("Area id is null");
        }

        AnalysisStats latestStats = analysisStatsRepository.findFirstByAreaIdOrderByTsDesc(areaId);
        if (latestStats == null) {
            return Collections.emptyList();
        }

        OffsetDateTime windowStart = latestStats.getTs().minusMinutes(predictionWindowMinutes);
        List<AnalysisStats> history = analysisStatsRepository.findRecentByAreaId(areaId, windowStart);

        int maxCapacity = areaService.getCapacity(areaId);

        int stepSeconds = defaultStepSize;
        if (history.size() >= 2) {
            long diff = ChronoUnit.SECONDS.between(
                    history.get(history.size() - 2).getTs(),
                    history.getLast().getTs());
            if (diff >= 2 && diff <= 120) {
                stepSeconds = (int) diff;
            }
        }

        return LinearRegressionPredictor.predictFutureTrend(history, predictionHorizonMinutes, stepSeconds,
                maxCapacity);
    }

    /**
     * Deletes all recorded analysis statistics.
     */
    public void deleteAnalysis() {
        analysisStatsRepository.deleteAll();
    }
}