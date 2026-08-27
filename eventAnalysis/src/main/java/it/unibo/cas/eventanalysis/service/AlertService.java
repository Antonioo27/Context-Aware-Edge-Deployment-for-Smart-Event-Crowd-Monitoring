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

        // Scatta l'alert in modo più facile: basta 1 HIGHLY_RISING o 2 RISING recenti
        if (dangerScore >= 2) {
            return Alert.builder()
                    .area_id(area.id())
                    .ts(OffsetDateTime.now())
                    .cause("Il trend della folla in quest'area è in forte crescita (Danger Score: " + dangerScore + "/3)")
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
