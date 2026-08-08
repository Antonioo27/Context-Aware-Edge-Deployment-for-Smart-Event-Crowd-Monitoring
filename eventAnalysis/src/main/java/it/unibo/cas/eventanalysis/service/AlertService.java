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
        int count_hr = 0;
        for (AnalysisStats analysisStats : analysisHistory.getAnalysisStats()) {
            if (analysisStats.getTrend() == Trend.HIGHLY_RISING)
                count_hr++;
        }
        if (count_hr > 3)
            return Alert.builder()
                    .area_id(area.id())
                    .ts(OffsetDateTime.now())
                    .cause("The trend of the crowd is highly rising in this area")
                    .build();
        else
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
