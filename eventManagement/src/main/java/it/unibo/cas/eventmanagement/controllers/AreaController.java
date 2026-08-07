package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.AreaDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.services.AreaService;
import it.unibo.cas.eventmanagement.services.KubernetesOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("api/event/")
public class AreaController {
    private static final Logger logger = LoggerFactory.getLogger(AreaController.class);

    @Autowired
    private AreaService areaService;
    @Autowired
    private KubernetesOrchestrationService kubernetesOrchestrationService;

    @PostMapping("area")
    public ResponseEntity<String> createArea(@RequestBody AreaDTO areaDTO) {
        Area area = areaService.createArea(areaDTO);
        try {
            kubernetesOrchestrationService.deployAnalysisForArea(area.getName());
            logger.info("Pod di analisi per l'area {} deployato su Kubernetes", area.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body("Area e relativo Pod creati con successo");
        } catch (Exception e) {
            logger.error("Area creata nel DB ma errore durante il deploy Kubernetes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Area salvata ma errore nella creazione del Pod K8s: " + e.getMessage());
        }
    }

    @GetMapping("areas")
    public ResponseEntity<List<Area>> getAllAreas() {
        return ResponseEntity.ok(areaService.getAllAreas());
    }

    @GetMapping("area/{areaId}")
    public ResponseEntity<Area> getArea(@PathVariable String areaId) {
        return ResponseEntity.ok(areaService.getArea(areaId));
    }

    @GetMapping("area/{areaId}/capacity")
    public ResponseEntity<Integer> getAreaCapacity(@PathVariable String areaId) {
        return ResponseEntity.ok(areaService.getCapacity(areaId));
    }

    @GetMapping("area/{areaId}/m2")
    public ResponseEntity<Double> getM2(@PathVariable String areaId) {
        return ResponseEntity.ok(areaService.getM2(areaId));
    }

    @PutMapping("area/{areaId}")
    public ResponseEntity<String> updateArea(@PathVariable Long areaId, @RequestBody AreaDTO areaDTO) {
        // TODO
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/area/{areaId}")
    public ResponseEntity<String> deleteArea(@PathVariable String areaId) {
        Area area = areaService.getArea(areaId); // lancia ResourceNotFoundException se non esiste
        areaService.deleteArea(areaId);
        
        try {
            kubernetesOrchestrationService.removeAnalysisForArea(area.getName());
            logger.info("Pod di analisi per l'area {} rimosso con successo da K8s", area.getName());
            return ResponseEntity.ok("Area e relativo Pod rimossi con successo");
        } catch (Exception e) {
            logger.error("Area rimossa dal DB ma errore durante la cancellazione del Pod K8s: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Area rimossa dal DB ma errore durante la cancellazione del Pod Kubernetes");
        }
    }
}