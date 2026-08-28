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

@Slf4j
@Service
public class AlertService {
    @Autowired
    private Area area;

    @Autowired
    private EventManagementClient eventManagementClient;

    @Autowired
    private MqttBrokerClient mqttBrokerClient;

    /**
     * Check if there are too many highly rising trends in the analysis history.
     * 
     * @param analysisHistory the analysis history
     * @return an Alert if there are too many highly rising trends,
     *         {@code null} otherwise
     */
    public Alert checkAlerts(AnalysisHistory analysisHistory) {
        List<AnalysisStats> stats = analysisHistory.getAnalysisStats();
        if (stats == null || stats.isEmpty())
            return null;

        int dangerScore = 0;
        // Analizziamo al massimo le ultime 3 finestre temporali
        int recentWindows = Math.min(stats.size(), 3);

        for (int i = stats.size() - 1; i >= stats.size() - recentWindows; i--) {
            AnalysisStats stat = stats.get(i);
            if (stat.getTrend() == Trend.HIGHLY_RISING) {
                dangerScore += 2;
            } else if (stat.getTrend() == Trend.RISING) {
                dangerScore += 1;
            } else if (stat.getTrend() == Trend.DOWNING) {
                dangerScore -= 1;
            } else if (stat.getTrend() == Trend.HIGHLY_DOWNING) {
                dangerScore -= 2;
            }
        }

        if (dangerScore >= 5) {
            return Alert.builder()
                    .area_id(area.id())
                    .ts(OffsetDateTime.now())
                    .cause("Il trend della folla in quest'area è in forte crescita (Danger Score: " + dangerScore
                            + "/3)")
                    .build();
        }

        return null;
    }

    /**
     * Send an alert to the event management service and to the broker.
     * 
     * @param alert the alert to send
     */
    public void emitDualPathAlert(Alert alert) {
        // 1. FAST-PATH: Invio immediato su MQTT locale (~1-2 ms)
        String topic = "event/alerts/" + alert.getArea_id();
        mqttBrokerClient.publishAlert(topic, alert);

        // 2. SLOW-PATH: Salvataggio asincrono su Backend PostGIS (~40 ms)
        try {
            eventManagementClient.sendAlert(alert);
            log.info("[SLOW-PATH] Alert inviato al Backend per persistenza su DB");
        } catch (Exception e) {
            log.error("Errore salvataggio alert su Backend: {}", e.getMessage());
        }
    }

}
