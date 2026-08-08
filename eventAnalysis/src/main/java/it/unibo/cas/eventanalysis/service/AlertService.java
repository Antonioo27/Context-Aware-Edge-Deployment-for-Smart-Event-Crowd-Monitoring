package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.models.entities.AnalysisHistory;
import it.unibo.cas.eventanalysis.models.entities.AnalysisStats;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    /**
     * Check if there are too many highly rising trends in the analysis history.
     * 
     * @param analysisHistory the analysis history
     */
    public void checkAlerts(AnalysisHistory analysisHistory) {
        int count_hr = 0;
        for (AnalysisStats analysisStats : analysisHistory.getAnalysisStats()) {
            if (analysisStats.getTrend() == Trend.HIGHLY_RISING)
                count_hr++;
        }
        if (count_hr > 3)
            sendAlert();

    }

    public void sendAlert() {
        // TODO call to backend sending an alert
    }

}
