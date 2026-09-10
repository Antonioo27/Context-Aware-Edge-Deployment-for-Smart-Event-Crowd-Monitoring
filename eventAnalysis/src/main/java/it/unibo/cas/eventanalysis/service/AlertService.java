package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.clients.MqttBrokerClient;
import it.unibo.cas.eventanalysis.models.entities.Alert;
import it.unibo.cas.eventanalysis.models.entities.AnalysisHistory;
import it.unibo.cas.eventanalysis.models.entities.AnalysisStats;
import it.unibo.cas.eventanalysis.models.entities.Area;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evaluates crowd growth patterns and manages safety alert dispatching within the event 
 * analysis microservice. Detect sustained crowd growth and triggers notifications using a dual-path communication pattern
 */
@Slf4j
@Service
public class AlertService {
    @Autowired
    private Area area;

    @Autowired
    private EventManagementClient eventManagementClient;

    @Autowired
    private MqttBrokerClient mqttBrokerClient;

    private int consecutiveStreak = 0;

    /**
     * Inspects the latest crowd trend from the analysis history and determines if an alert must
     * be raised based on sustained HIGHLY_RISING patterns.
     * 
     * @param analysisHistory analysisHistory the chronological record of recent density and trend evaluations
     * @return an {@link Alert} instance if the trigger conditions are met, null otherwise
     */
    public Alert checkAlerts(AnalysisHistory analysisHistory) {
        List<AnalysisStats> stats = analysisHistory.getAnalysisStats();
        if (stats == null || stats.isEmpty()) {
            return null;
        }

        AnalysisStats latestStat = stats.get(stats.size() - 1);
        if (latestStat.getTrend() == Trend.HIGHLY_RISING) {
            consecutiveStreak++;
        } else {
            consecutiveStreak = 0; 
            return null;
        }

        if (consecutiveStreak >= 3 && (consecutiveStreak - 3) % 3 == 0) {
            log.warn("[ALERT TRIGGER] Area {}: rilevati {} HIGHLY_RISING consecutivi (Densità: {} pers/m²)", 
                    area.id(), consecutiveStreak, String.format("%.2f", latestStat.getDensity()));

            return Alert.builder()
                    .area_id(area.id())
                    .ts(OffsetDateTime.now())
                    .cause(String.format("Forte crescita sostenuta della folla (%d trend HIGHLY_RISING consecutivi)", consecutiveStreak))
                    .build();
        }

        return null;
    }

    /**
     * Transmits the alert over the local MQTT broker for immediate frontend reactivity
     * and sends it to the central backend via REST for database storage.
     * 
     * @param alert the alert to send
     */
    public void emitDualPathAlert(Alert alert) {
        String topic = "event/alerts/" + alert.getArea_id();
        mqttBrokerClient.publishAlert(topic, alert);

        try {
            eventManagementClient.sendAlert(alert);
            log.info("[SLOW-PATH] Alert inviato al Backend per persistenza su DB");
        } catch (Exception e) {
            log.error("Errore salvataggio alert su Backend: {}", e.getMessage());
        }
    }

}
