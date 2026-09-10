package it.unibo.cas.eventanalysis.service;

import it.unibo.cas.eventanalysis.models.entities.*;
import it.unibo.cas.eventanalysis.models.enums.Trend;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** 
 * Service that performs crowd analysis for a monitored area.
 * 
 * Responsabilites : 
 * - Extracts unique device MAC addresses from probe batches to avoid counting duplicates.
 * - Estimates the total number of people using a Poisson probability model based on device transmission intervals.
 * - Computes the effective time window dynamically to prevent wrong estimations during startup or pod migration.
 * - Computes crowd density (people per square meter) using the area dimensions.
 * - Determines the crowd trend (such as STABLE, RISING, or HIGHLY_RISING) using linear regression on recent density values.
 * 
*/
@Service
public class AnalysisService {
    @Getter
    @Value("${eventanalysis.analysis.windows-size:60}")
    private int windowSize;

    @Getter
    @Value("${analysis.slide-step:5.0}")
    private double slideStep;

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
     * Estimates the total population by checking the real time interval covered by batches in memory.
     * Using an effective window prevents underestimating people when the pod starts or migrates.
     *
     * @param probeBatches list of probe batches in memory
     * @return estimated number of people in the area
     */
    public long estimatePeople(List<ProbeBatch> probeBatches) {
        if (probeBatches == null || probeBatches.isEmpty()) {
            return 0;
        }

        int distinctMac = countNumDifferentMac(probeBatches);

        OffsetDateTime minTs = probeBatches.stream()
                .map(ProbeBatch::getSentAt)
                .filter(Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        OffsetDateTime maxTs = probeBatches.stream()
                .map(ProbeBatch::getSentAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        double effectiveWindow;
        if (minTs != null && maxTs != null) {
            double spanSeconds = Math.abs(Duration.between(minTs, maxTs).toMillis() / 1000.0);
            
            double realCoverage = spanSeconds + 1.0;

            effectiveWindow = Math.min(windowSize, Math.max(slideStep, realCoverage));
        } else {
            effectiveWindow = windowSize;
        }

        return estimatePeople(distinctMac, effectiveWindow);
    }

    /**
     * Calculates the estimated number of people using the formula: N = distinctMac / (1 - exp(-effectiveWindow / mean)).
     *
     * @param numDifferentMac number of unique MAC addresses detected
     * @param effectiveWindow actual time duration in seconds covered by the data
     * @return estimated population count
     */
    public long estimatePeople(int numDifferentMac, double effectiveWindow) {
        if (effectiveWindow <= 0.0) {
            effectiveWindow = windowSize;
        }
        double denominator = 1.0 - Math.exp(-effectiveWindow / mean);
        if (denominator <= 0.0) {
            return 0;
        }
        return Math.round(numDifferentMac / denominator);
    }
    
    public double density(long estimatedPeople) {
        return estimatedPeople / area.m2();
    }

    /**
     * Computes the crowd trend by applying linear regression on recent density values.
     * Requires at least 3 historical points to produce a valid trend.
     *
     * @param previousStats list of historical analysis records
     * @return the classified Trend, or NOT_ENOUGH_VALUES if there are fewer than 3 points
     */
    public Trend calculateTrend(List<AnalysisStats> previousStats) {
        if (previousStats == null || previousStats.size() < 3) {
            return Trend.NOT_ENOUGH_VALUES;
        }

        int maxPoints = (int) Math.max(5, Math.round(windowSize / slideStep));
        int n = Math.min(previousStats.size(), maxPoints);
        List<AnalysisStats> recent = previousStats.subList(previousStats.size() - n, previousStats.size());

        double slope = getSlope(recent);
        return getTrend(slope);
    }

    /**
     * Converts a regression slope into an angle in degrees and maps it to a Trend category.
     *
     * @param slope linear regression slope value
     * @return matching Trend classification
     */
    private @NonNull Trend getTrend(double slope) {
        double angleInDegrees = Math.toDegrees(Math.atan(slope));
        Trend trend;
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

    /**
     * Calculates the slope of the line that best fits the density values using ordinary least squares.
     *
     * @param statsList list of recent analysis statistics
     * @return the computed slope value
     */
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
