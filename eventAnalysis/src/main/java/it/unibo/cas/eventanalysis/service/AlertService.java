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

    // Contatore di stato persistente nel Pod (non limitato dalla dimensione di AnalysisHistory)
    private int consecutiveStreak = 0;

    /**
     * Check if there are 4 or more consecutive highly rising trends in the analysis
     * history.
     * 
     * @param analysisHistory the analysis history
     * @return an Alert if there are 4 or more consecutive highly rising trends,
     *         {@code null} otherwise
     */
    public Alert checkAlerts(AnalysisHistory analysisHistory) {
        List<AnalysisStats> stats = analysisHistory.getAnalysisStats();
        if (stats == null || stats.isEmpty()) {
            return null;
        }

        AnalysisStats latestStat = stats.get(stats.size() - 1);
        // Aggiornamento dello streak basato sull'ultimo trend calcolato
        if (latestStat.getTrend() == Trend.HIGHLY_RISING) {
            consecutiveStreak++;
        } else {
            consecutiveStreak = 0; // Se il trend cambia (STABLE, RISING, DOWNING), la serie si azzera
            return null;
        }

        // Regola modulare: scatta a 3 (1° alert), silenzio a 4,5 scatta a 5 (2° alert), silenzio a 6,7 scatta a 8 (3° alert)...
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
