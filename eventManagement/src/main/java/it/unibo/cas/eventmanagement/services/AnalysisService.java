package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.PredictionPointDTO;
import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import it.unibo.cas.eventmanagement.models.enums.State;
import it.unibo.cas.eventmanagement.repositories.AnalysisStatsRepository;
import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.services.prediction.LinearRegressionPredictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import java.util.List;

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

    @Autowired
    private AnalysisStatsRepository analysisStatsRepository;

    @Autowired
    private AreaService areaService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotifyService notifyService;

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

        // Esegue la predizione dell'affollamento futuro
        checkPrediction(as);

        return as;
    }

    /**
     * Esegue la predizione dell'affollamento e lancia un alert proattivo se
     * necessario.
     * 
     * @param latestStats l'ultima statistica arrivata
     */
    private void checkPrediction(AnalysisStats latestStats) {
        if (latestStats.getTs() == null) {
            return;
        }

        // Recuperiamo lo storico recente
        OffsetDateTime windowStart = latestStats.getTs().minusMinutes(predictionWindowMinutes);
        List<AnalysisStats> history = analysisStatsRepository.findRecentByAreaId(latestStats.getAreaId(), windowStart);

        Double predictedPeople = LinearRegressionPredictor.predictFutureCrowd(history, predictionHorizonMinutes);

        if (predictedPeople != null) {
            log.info("Predicted people in area {} in {} minutes: {}", latestStats.getAreaId(), predictionHorizonMinutes,
                    predictedPeople.intValue());

            int maxCapacity = areaService.getCapacity(latestStats.getAreaId());
            double predictedDensity = predictedPeople / maxCapacity;

            if (predictedDensity > criticalThreshold) {
                log.warn("PREDICTION ALERT: Area {} will become CRITICAL in {} minutes!", latestStats.getAreaId(),
                        predictionHorizonMinutes);

                AlertDTO alertDTO = AlertDTO.builder()
                        .area_id(latestStats.getAreaId())
                        .ts(OffsetDateTime.now())
                        .cause(String.format(
                                "Predizione: l'area supererà la soglia critica tra %d minuti. (Stima: %d persone)",
                                predictionHorizonMinutes, predictedPeople.intValue()))
                        .build();

                // Creiamo l'alert e lo notifichiamo
                Alert alert = alertService.addAlert(alertDTO);
                notifyService.notifyAutomaticAlert(alert);
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
     * Calcola e restituisce la lista dei punti futuri predetti (trend) per il
     * front-end.
     * 
     * @param areaId l'id dell'area
     * @return lista di DTO contenenti il timestamp e il numero di persone stimate
     *         nel futuro
     */
    public List<PredictionPointDTO> getPredictionTrendByArea(String areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("Area id is null");
        }

        // Recuperiamo l'ultimo dato registrato per l'area
        AnalysisStats latestStats = analysisStatsRepository.findFirstByAreaIdOrderByTsDesc(areaId);
        if (latestStats == null) {
            return java.util.Collections.emptyList();
        }

        // Recuperiamo la finestra storica basandoci sul timestamp più recente
        OffsetDateTime windowStart = latestStats.getTs().minusMinutes(predictionWindowMinutes);
        List<AnalysisStats> history = analysisStatsRepository.findRecentByAreaId(areaId, windowStart);

        // Se disponibile, usiamo la window size originale come "passo" temporale (step)
        // altrimenti usiamo un default (es. 20 secondi) per la densità dei punti sul
        // grafico
        int stepSeconds = latestStats.getWindowSeconds() > 0 ? latestStats.getWindowSeconds() : 20;

        return LinearRegressionPredictor.predictFutureTrend(history, predictionHorizonMinutes, stepSeconds);
    }
}