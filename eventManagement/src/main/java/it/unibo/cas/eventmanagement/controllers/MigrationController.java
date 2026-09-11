package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.MigrationDTO;
import it.unibo.cas.eventmanagement.services.MigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing pod migration history.
 *
 * Responsibilities:
 * - Exposes HTTP endpoints consumed by the frontend dashboard to show container migrations between Edge and Cloud nodes.
 * - Provides historical records of orchestration actions such as failover events, CPU overload avoidance, and cost optimizations.
 */
@RestController
@RequestMapping("/api/orchestration/migrations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MigrationController {

    private final MigrationService migrationService;

    @GetMapping
    public ResponseEntity<List<MigrationDTO>> getAllMigrations() {
        return ResponseEntity.ok(migrationService.getAllMigrations());
    }

    @GetMapping("/area/{areaId}")
    public ResponseEntity<List<MigrationDTO>> getMigrationsByArea(@PathVariable String areaId) {
        return ResponseEntity.ok(migrationService.getMigrationsByArea(areaId));
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAllMigrations() {
        migrationService.deleteAllMigrations();
        return ResponseEntity.ok("Tutte le migrazioni sono state eliminate con successo.");
    }
}