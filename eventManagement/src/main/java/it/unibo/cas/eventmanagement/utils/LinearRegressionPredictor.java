package it.unibo.cas.eventmanagement.utils;

import it.unibo.cas.eventmanagement.models.DTOs.PredictionPointDTO;
import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LinearRegressionPredictor {

    /**
     * Prevede il numero di persone future basandosi su una regressione lineare
     * semplice
     * degli storici recenti.
     *
     * @param history       storico delle analisi (deve essere ordinato per
     *                      timestamp)
     * @param futureMinutes quanti minuti nel futuro prevedere (rispetto all'ultimo
     *                      record)
     * @return stima del numero di persone (>= 0) oppure null se i dati sono
     *         insufficienti
     */
    public static Double predictFutureCrowd(List<AnalysisStats> history, int futureMinutes) {
        if (history == null || history.size() < 2) {
            return null; // Troppo pochi dati per tracciare una retta
        }

        // Usiamo il timestamp più vecchio come origine (x = 0) per stabilità numerica
        OffsetDateTime origin = history.getFirst().getTs();

        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;
        int n = history.size();

        for (AnalysisStats stat : history) {
            // Asse X: minuti trascorsi dall'origine
            double x = ChronoUnit.SECONDS.between(origin, stat.getTs()) / 60.0;
            // Asse Y: persone stimate
            double y = stat.getEstimatedPeople();

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double denominator = sumX2 - n * meanX * meanX;
        if (denominator == 0) {
            return null; // Tutti i punti hanno lo stesso X, impossibile calcolare la pendenza
        }

        // Formula della pendenza (slope) per minimi quadrati
        double slope = (sumXY - n * meanX * meanY) / denominator;

        // Intercetta
        double intercept = meanY - slope * meanX;

        // Calcoliamo la X futura (minuti dall'origine + orizzonte futuro)
        OffsetDateTime lastRecord = history.get(n - 1).getTs();
        double futureX = (ChronoUnit.SECONDS.between(origin, lastRecord) / 60.0) + futureMinutes;

        // Predizione y = mx + q
        double prediction = slope * futureX + intercept;

        // L'affollamento non può essere negativo
        return Math.max(0.0, prediction);
    }

    /**
     * Calcola un trend di predizione futuro restituendo una lista di punti
     *
     * @param history       storico delle analisi
     * @param futureMinutes minuti totali nel futuro da predire
     * @param stepSeconds   la granularità della predizione (es. un punto ogni 20
     *                      secondi)
     * @return lista di punti predetti nel futuro
     */
    public static List<PredictionPointDTO> predictFutureTrend(
            List<AnalysisStats> history, int futureMinutes, int stepSeconds) {
        if (history == null || history.size() < 2) {
            return Collections.emptyList();
        }

        OffsetDateTime origin = history.getFirst().getTs();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = history.size();

        for (AnalysisStats stat : history) {
            double x = ChronoUnit.SECONDS.between(origin, stat.getTs()) / 60.0;
            double y = stat.getEstimatedPeople();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double meanX = sumX / n;
        double meanY = sumY / n;
        double denominator = sumX2 - n * meanX * meanX;

        if (denominator == 0) {
            return Collections.emptyList();
        }

        double slope = (sumXY - n * meanX * meanY) / denominator;
        double intercept = meanY - slope * meanX;

        OffsetDateTime lastRecord = history.get(n - 1).getTs();
        List<PredictionPointDTO> predictions = new ArrayList<>();

        int totalSecondsToPredict = futureMinutes * 60;
        if (stepSeconds <= 0)
            stepSeconds = 60; // default 1 minuto

        for (int i = stepSeconds; i <= totalSecondsToPredict; i += stepSeconds) {
            OffsetDateTime futureTime = lastRecord.plusSeconds(i);
            double futureX = ChronoUnit.SECONDS.between(origin, futureTime) / 60.0;
            double pred = Math.max(0.0, slope * futureX + intercept);
            predictions.add(new PredictionPointDTO(futureTime, pred));
        }

        return predictions;
    }
}
