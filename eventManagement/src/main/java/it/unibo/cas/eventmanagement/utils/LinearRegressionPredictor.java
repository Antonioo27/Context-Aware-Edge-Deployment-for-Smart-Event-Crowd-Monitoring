package it.unibo.cas.eventmanagement.utils;

import it.unibo.cas.eventmanagement.models.DTOs.PredictionPointDTO;
import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import it.unibo.cas.eventmanagement.models.enums.Trend;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for crowd forecasting using weighted linear regression
 * with recency weighting, damped trend projection, and capacity constraints.
 */
public class LinearRegressionPredictor {

    private static final double RECENCY_TAU_SECONDS = 60.0;
    private static final double DAMPING_GAMMA = 0.35;

    /**
     * Forecasts the future crowd size using weighted linear regression and damped
     * trend projection.
     *
     * @param history       chronologically ordered list of historical analysis
     *                      statistics
     * @param futureMinutes forecast horizon in minutes
     * @return predicted number of people (>= 0), or null if history is insufficient
     */
    public static Double predictFutureCrowd(List<AnalysisStats> history, int futureMinutes) {
        return predictFutureCrowd(history, futureMinutes, null);
    }

    /**
     * Forecasts the future crowd size bounded by maximum area capacity.
     *
     * @param history       chronologically ordered list of historical analysis
     *                      statistics
     * @param futureMinutes forecast horizon in minutes
     * @param maxCapacity   maximum area capacity limit (null for unbounded upper
     *                      limit)
     * @return predicted number of people clamped between 0 and maxCapacity, or null
     *         if history is insufficient
     */
    public static Double predictFutureCrowd(List<AnalysisStats> history, int futureMinutes, Integer maxCapacity) {
        if (history == null || history.size() < 2) {
            return null;
        }

        double slope = calculateWeightedSlope(history);
        AnalysisStats lastStat = history.getLast();
        double basePeople = lastStat.getEstimatedPeople();

        if (lastStat.getTrend() == Trend.STABLE) {
            slope *= 0.1;
        }

        double deltaMinutes = Math.max(0.0, futureMinutes);
        double deltaY = calculateDampedDelta(slope, deltaMinutes);
        double prediction = basePeople + deltaY;

        return clampPrediction(prediction, maxCapacity);
    }

    /**
     * Generates a list of predicted trend points at the specified step interval.
     *
     * @param history       chronologically ordered list of historical analysis
     *                      statistics
     * @param futureMinutes total future horizon to predict in minutes
     * @param stepSeconds   time step in seconds between consecutive prediction
     *                      points
     * @return list of prediction points over time
     */
    public static List<PredictionPointDTO> predictFutureTrend(
            List<AnalysisStats> history, int futureMinutes, int stepSeconds) {
        return predictFutureTrend(history, futureMinutes, stepSeconds, null);
    }

    /**
     * Generates a list of predicted trend points bounded by maximum area capacity.
     *
     * @param history       chronologically ordered list of historical analysis
     *                      statistics
     * @param futureMinutes total future horizon to predict in minutes
     * @param stepSeconds   time step in seconds between consecutive prediction
     *                      points
     * @param maxCapacity   maximum area capacity limit (null for unbounded upper
     *                      limit)
     * @return list of prediction points clamped to the area capacity
     */
    public static List<PredictionPointDTO> predictFutureTrend(
            List<AnalysisStats> history, int futureMinutes, int stepSeconds, Integer maxCapacity) {
        if (history == null || history.size() < 2) {
            return Collections.emptyList();
        }

        double slope = calculateWeightedSlope(history);
        AnalysisStats lastStat = history.getLast();
        double basePeople = lastStat.getEstimatedPeople();

        if (lastStat.getTrend() == Trend.STABLE) {
            slope *= 0.1;
        }

        OffsetDateTime lastRecord = lastStat.getTs();
        List<PredictionPointDTO> predictions = new ArrayList<>();

        int totalSecondsToPredict = futureMinutes * 60;
        if (stepSeconds <= 0) {
            stepSeconds = 15;
        }

        for (int i = stepSeconds; i <= totalSecondsToPredict; i += stepSeconds) {
            OffsetDateTime futureTime = lastRecord.plusSeconds(i);
            double deltaMinutes = i / 60.0;
            double deltaY = calculateDampedDelta(slope, deltaMinutes);
            double pred = clampPrediction(basePeople + deltaY, maxCapacity);
            predictions.add(new PredictionPointDTO(futureTime, pred));
        }

        if (totalSecondsToPredict > 0 && totalSecondsToPredict % stepSeconds != 0) {
            OffsetDateTime futureTime = lastRecord.plusSeconds(totalSecondsToPredict);
            double deltaMinutes = totalSecondsToPredict / 60.0;
            double deltaY = calculateDampedDelta(slope, deltaMinutes);
            double pred = clampPrediction(basePeople + deltaY, maxCapacity);
            predictions.add(new PredictionPointDTO(futureTime, pred));
        }

        return predictions;
    }

    /**
     * Calculates the time-weighted slope using Weighted Least Squares (WLS).
     *
     * @param history chronologically ordered list of historical analysis statistics
     * @return calculated slope in estimated people per minute
     */
    private static double calculateWeightedSlope(List<AnalysisStats> history) {
        OffsetDateTime origin = history.getFirst().getTs();
        OffsetDateTime lastTs = history.getLast().getTs();

        int n = history.size();
        double sumW = 0.0;
        double sumWX = 0.0;
        double sumWY = 0.0;

        double[] x = new double[n];
        double[] y = new double[n];
        double[] w = new double[n];

        for (int i = 0; i < n; i++) {
            AnalysisStats stat = history.get(i);
            x[i] = ChronoUnit.SECONDS.between(origin, stat.getTs()) / 60.0;
            y[i] = stat.getEstimatedPeople();

            long ageSeconds = ChronoUnit.SECONDS.between(stat.getTs(), lastTs);
            w[i] = Math.exp(-Math.max(0, ageSeconds) / RECENCY_TAU_SECONDS);

            sumW += w[i];
            sumWX += w[i] * x[i];
            sumWY += w[i] * y[i];
        }

        if (sumW == 0.0) {
            return 0.0;
        }

        double meanX = sumWX / sumW;
        double meanY = sumWY / sumW;

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            numerator += w[i] * dx * dy;
            denominator += w[i] * dx * dx;
        }

        if (denominator == 0.0) {
            return 0.0;
        }

        return numerator / denominator;
    }

    /**
     * Computes the damped delta over a given future time horizon.
     *
     * @param slope        rate of change in people per minute
     * @param deltaMinutes future time horizon in minutes
     * @return damped change in people count
     */
    private static double calculateDampedDelta(double slope, double deltaMinutes) {
        if (deltaMinutes <= 0.0) {
            return 0.0;
        }
        return slope * (1.0 - Math.exp(-DAMPING_GAMMA * deltaMinutes)) / DAMPING_GAMMA;
    }

    /**
     * Clamps a prediction value to positive values and maximum capacity when
     * provided.
     *
     * @param prediction  raw prediction value
     * @param maxCapacity maximum area capacity limit (optional)
     * @return clamped prediction value
     */
    private static double clampPrediction(double prediction, Integer maxCapacity) {
        double clamped = Math.max(0.0, prediction);
        if (maxCapacity != null && maxCapacity > 0) {
            clamped = Math.min(clamped, (double) maxCapacity);
        }
        return clamped;
    }
}
