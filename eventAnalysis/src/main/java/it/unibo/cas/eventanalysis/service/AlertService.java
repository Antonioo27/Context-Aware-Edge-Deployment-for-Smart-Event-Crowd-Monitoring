package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.models.entities.Alert;
import it.unibo.cas.eventanalysis.models.entities.AnalysisHistory;
import it.unibo.cas.eventanalysis.models.entities.AnalysisStats;
import it.unibo.cas.eventanalysis.models.entities.Area;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlertService {
    @Autowired
    private Area area;

    @Autowired
    private EventManagementClient eventManagementClient;

    /**
     * Check if there are too many highly rising trends in the analysis history.
     * 
     * @param analysisHistory the analysis history
     * @return an Alert if there are too many highly rising trends,
     *         {@code null} otherwise
     */
    public Alert checkAlerts(AnalysisHistory analysisHistory) {
        List<AnalysisStats> stats = analysisHistory.getAnalysisStats();
        if(stats == null || stats.isEmpty()) 
            return null;

        int consecutiveHighlyRising = 0;
        for (int i = stats.size() - 1; i >= 0; i--) {
            AnalysisStats stat = stats.get(i);
            if (stat.getTrend() == Trend.HIGHLY_RISING) {
                consecutiveHighlyRising++;
            } else {
                // La sequenza consecutiva si interrompe
                break;
            }
        }

        // Scatta l'alert al 3°, 4°, 5°... HIGHLY_RISING consecutivo
        if (consecutiveHighlyRising >= 3) {
            return Alert.builder()
                    .area_id(area.id())
                    .ts(OffsetDateTime.now())
                    .cause("The trend of the crowd is highly rising in this area (consecutive: " + consecutiveHighlyRising + ")")
                    .build();
        }
        
        
        return null;
    }

    /**
     * Send an alert to the event management service.
     * 
     * @param alert the alert to send
     */
    public void sendAlert(Alert alert) {
        eventManagementClient.sendAlert(alert);
    }

}
