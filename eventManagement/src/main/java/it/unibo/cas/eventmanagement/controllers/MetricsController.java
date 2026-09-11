package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.SystemMetricsDTO;
import it.unibo.cas.eventmanagement.services.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that exposes global system metrics for the crowd monitoring platform.
 *
 * Responsibilities:
 * - Provides an HTTP API endpoint consumed by the frontend dashboard to monitor system performance.
 * - Aggregates simulated network latencies (Slow-Path and Fast-Path), total processed probe batches,
 *   active operational alerts, node CPU usage, and pod allocation distributions.
 */
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