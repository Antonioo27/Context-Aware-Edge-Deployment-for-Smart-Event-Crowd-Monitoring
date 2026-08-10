package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.models.entities.*;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class AnalysisService {
    @Getter
    @Value("${eventanalysis.analysis.windows-size:60}")
    private int windowSize;

    @Value("${eventanalysis.analysis.mean-exp-int:25}")
    private int mean;

    @Value("${eventanalysis.analysis.trend-max-stable-angle:1.0}")
    private double trend_stable;

    @Value("${eventanalysis.analysis.trend-highly-angle:2.5}")
    private double trend_highly;

    @Autowired
    private Area area;

    public AnalysisService() {
        if (trend_stable > 90 || trend_stable < 0)
            trend_stable = 10;
        if (trend_highly > 90 || trend_highly < 0)
            trend_highly = 45;
    }

    /**
     * Count the number of different MAC addresses in the given probe batches.
     * 
     * @param probeBatches the probe batches to count the number of different MAC
     *                     addresses
     * @return the number of different MAC addresses
     */
    public int countNumDifferentMac(List<ProbeBatch> probeBatches) {
        HashMap<String, Probe> probeMap = new HashMap<>();
        for (ProbeBatch probeBatch : probeBatches) {
            for (Probe probe : probeBatch.getProbes())
                if (!probeMap.containsKey(probe.mac()))
                    probeMap.put(probe.mac(), probe);
        }
        return probeMap.size();
    }

    /**
     * Estimate the number of people in the given probe batches.
     * 
     * @param probeBatches the probe batches to estimate the number of people
     * @return the estimated number of people
     */
    public long estimatePeople(List<ProbeBatch> probeBatches) {
        return estimatePeople(countNumDifferentMac(probeBatches));
    }

    /**
     * Estimate the number of people based on the number of different MAC addresses.
     * 
     * @param numDifferentMac the number of different MAC addresses
     * @return the estimated number of people
     */
    public long estimatePeople(int numDifferentMac) {
        return Math.round(
                numDifferentMac / (1 - Math.exp(((double) -windowSize / mean))));
    }

    /**
     * Calculate the density of the crowd.
     * 
     * @param estimatedPeople the estimated number of people
     * @return the density of the crowd
     */
    public double density(long estimatedPeople) {
        return estimatedPeople / area.m2();
    }

    /**
     * Calculate the density of the crowd.
     * 
     * @param probeBatches the probe batches to estimate the density
     * @return the density of the crowd
     */
    public double density(List<ProbeBatch> probeBatches) {
        return estimatePeople(probeBatches) / area.m2();
    }

    /**
     * Calculate the trend of the crowd density based on the previous density
     * values.
     * 
     * @param previousStats the previous density values (at least 5)
     * @return the trend of the crowd density
     */
    public Trend calculateTrend(List<AnalysisStats> previousStats) {
        if (previousStats== null || previousStats.size() < 3) {
            return Trend.NOT_ENOUGH_VALUES;
        }

        int n = Math.min(previousStats.size(), 5);
        List<AnalysisStats> recent = previousStats.subList(previousStats.size() - n, previousStats.size());

        // Perform linear regression on the last 5 values to find the slope
        // Calculate the slope based on density to ensure it's area-independent
        double slope = getSlope(recent);

        return getTrend(slope);
    }

    private @NonNull Trend getTrend(double slope) {
        // Convert the slope to an angle in degrees [-90, 90]
        double angleInDegrees = Math.toDegrees(Math.atan(slope));
        Trend trend;
        // Classify the trend based on the calculated angle
        if (angleInDegrees > trend_highly) {
            trend = Trend.HIGHLY_RISING;
        } else if (angleInDegrees > trend_stable) {
            trend = Trend.RISING;
        } else if (angleInDegrees >= -trend_stable) {
            trend = Trend.STABLE;
        } else if (angleInDegrees >= -trend_highly) {
            trend = Trend.DOWNING;
        } else {
            trend = Trend.HIGHLY_DOWNING;
        }
        return trend;
    }

    private static double getSlope(List<AnalysisStats> statsList) {
        int n = statsList.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = statsList.get(i).getDensity();

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - (sumX * sumX));
    }
}
