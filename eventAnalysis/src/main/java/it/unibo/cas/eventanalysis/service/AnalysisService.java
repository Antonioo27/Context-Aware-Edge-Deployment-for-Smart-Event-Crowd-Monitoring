package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.models.entities.*;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class AnalysisService {
    @Value("${eventanalysis.analysis.windows-size:60}")
    private int windowSize;

    @Value("${eventanalysis.analysis.mean-exp-int:25}")
    private int mean;

    @Value("${eventanalysis.analysis.trend-max-stable-angle:10}")
    private int trend_stable;

    @Value("${eventanalysis.analysis.trend-highly-angle:45}")
    private int trend_highly;

    private final AnalysisHistory analysisHistory;

    @Autowired
    private Area area;

    public AnalysisService() {
        this.analysisHistory = new AnalysisHistory();
        if(trend_stable > 90 || trend_stable < 0)
            trend_stable = 10;
        if(trend_highly > 90 || trend_highly < 0)
            trend_highly = 45;
    }

    public int countNumDifferentMac(List<ProbeBatch> probeBatches) {
        HashMap<String, Probe> probeMap = new HashMap<>();
        for (ProbeBatch probeBatch : probeBatches) {
            for (Probe probe : probeBatch.getProbes())
                if (!probeMap.containsKey(probe.mac()))
                    probeMap.put(probe.mac(), probe);
        }
        return probeMap.size();
    }

    public long estimatePeople(List<ProbeBatch> probeBatches){
        return estimatePeople(countNumDifferentMac(probeBatches));

    }

    public long estimatePeople(int numDifferentMac){
        return Math.round(
                numDifferentMac / (1 - Math.exp(((double) -windowSize / mean))));
    }

    public double density(long estimatedPeople) {
        return estimatedPeople / area.m2();
    }

    public double density(List<ProbeBatch> probeBatches) {
        return estimatePeople(probeBatches) / area.m2();
    }

    public void calculateTrend(){

    }

    public Trend calculateTrend(long estimatedPeople, double density) {
        AnalysisStats analysisStats = AnalysisStats.builder()
                .estimatedPeople(estimatedPeople)
                .density(density)
                .latency(0.0) // todo: get a true value of latency
                .build();

        analysisHistory.addAnalysisStats(analysisStats);
        
        List<AnalysisStats> statsList = analysisHistory.getAnalysisStats();
        if(statsList.size() < 5) {
            return Trend.NOT_ENOUGH_VALUES;
        }

        // Perform linear regression on the last 5 values to find the slope
        // Calculate the slope based on density to ensure it's area-independent
        double slope = getSlope(statsList);

        // Convert the slope to an angle in degrees [-90, 90]
        double angleInDegrees = Math.toDegrees(Math.atan(slope));

        // Classify the trend based on the calculated angle
        if (angleInDegrees > trend_highly) {
            analysisStats.setTrend(Trend.HIGHLY_RISING);
            return Trend.HIGHLY_RISING;
        } else if (angleInDegrees > trend_stable) {
            analysisStats.setTrend(Trend.RISING);
            return Trend.RISING;
        } else if (angleInDegrees >= -trend_stable) {
            analysisStats.setTrend(Trend.STABLE);
            return Trend.STABLE;
        } else if (angleInDegrees >= -trend_highly) {
            analysisStats.setTrend(Trend.DOWNING);
            return Trend.DOWNING;
        } else {
            analysisStats.setTrend(Trend.HIGHLY_DOWNING);
            return Trend.HIGHLY_DOWNING;
        }
    }

    private static double getSlope(List<AnalysisStats> statsList) {
        int n = statsList.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;

        for (int i = 0; i < n; i++) {
            // Using indices 1, 2, 3, 4, 5 as x (time) values
            double x = i + 1;
            // Using density (people/m2) instead of estimatedPeople for a fair comparison across areas
            double y = statsList.get(i).getDensity();

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - (sumX * sumX));
    }
}
