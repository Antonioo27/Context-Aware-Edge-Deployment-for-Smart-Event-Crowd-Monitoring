package it.unibo.cas.eventanalysis.models.entities;

import lombok.Data;
import lombok.Getter;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;

/**
 *
 */
@Data
@Getter
public class AnalysisHistory {
    private ArrayList<AnalysisStats> analysisStats = new ArrayList<>();

    public void addAnalysisStats(AnalysisStats stats) {
        analysisStats.add(stats);
        if (analysisStats.size() > 5)
            analysisStats.removeFirst();
    }
}