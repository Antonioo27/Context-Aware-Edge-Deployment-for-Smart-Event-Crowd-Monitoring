package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.AnalysisStatsDTO;
import it.unibo.cas.eventmanagement.models.DTOs.PredictionPointDTO;
import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import it.unibo.cas.eventmanagement.services.AnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/event")
public class AnalysisController {
    @Autowired
    private AnalysisService analysisService;

    @PostMapping("area/analysis")
    public ResponseEntity<String> sendAnalysis(@RequestBody AnalysisStatsDTO analysisStatsDTO) {
        if (analysisStatsDTO == null) {
            return ResponseEntity.badRequest().body("Area statistical analysis is null");
        }
        try {
            AnalysisStats analysisStats = AnalysisStats.builder()
                    .areaId(analysisStatsDTO.getArea_id())
                    .ts(analysisStatsDTO.getTs())
                    .windowSeconds(analysisStatsDTO.getWindow_seconds() != null ? analysisStatsDTO.getWindow_seconds() : 0)
                    .estimatedPeople(analysisStatsDTO.getEstimatedPeople() != null ? analysisStatsDTO.getEstimatedPeople() : 0L)
                    .trend(analysisStatsDTO.getTrend())
                    .servedBy(analysisStatsDTO.getServed_by())
                    .density(analysisStatsDTO.getDensity() != null ? analysisStatsDTO.getDensity() : 0.0)
                    .node(analysisStatsDTO.getNode())
                    .build();

            if (analysisService.addAnalysis(analysisStats) != null)
                return ResponseEntity.ok("Analysis got with success");
            else
                return ResponseEntity.badRequest().body("Analysis got with error");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("area/{areaId}/analysis-all")
    public ResponseEntity<List<AnalysisStats>> getAllAnalysis(@PathVariable String areaId) {
        if (areaId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<AnalysisStats> analysisStats;
        try {
            analysisStats = analysisService.getStatsByArea(areaId, AnalysisStats.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return  ResponseEntity.ok(analysisStats);
    }

    @GetMapping("area/{areaId}/analysis-people")
    public ResponseEntity<List<Long>> getPeopleAnalysis(@PathVariable String areaId) {
        if (areaId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Long> analysisStats;
        try {
            analysisStats = analysisService.getStatsByArea(areaId, Long.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return  ResponseEntity.ok(analysisStats);
    }

    @GetMapping("area/{areaId}/analysis-density")
    public ResponseEntity<List<Double>> getDensityAnalysis(@PathVariable String areaId) {
        if (areaId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Double> analysisStats;
        try {
            analysisStats = analysisService.getStatsByArea(areaId, Double.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return  ResponseEntity.ok(analysisStats);
    }

    @GetMapping("area/{areaId}/prediction")
    public ResponseEntity<List<PredictionPointDTO>> getPredictionTrend(@PathVariable String areaId) {
        if (areaId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            List<PredictionPointDTO> predictions = analysisService.getPredictionTrendByArea(areaId);
            return ResponseEntity.ok(predictions);
        } catch (Exception e) {
            log.error("Error retrieving prediction for area {}: {}", areaId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}