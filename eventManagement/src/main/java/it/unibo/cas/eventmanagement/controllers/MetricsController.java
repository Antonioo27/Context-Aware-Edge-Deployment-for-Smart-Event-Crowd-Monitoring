package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.SystemMetricsDTO;
import it.unibo.cas.eventmanagement.services.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    @GetMapping
    public ResponseEntity<SystemMetricsDTO> getMetrics() {
        return ResponseEntity.ok(metricsService.getSystemMetrics());
    }
}