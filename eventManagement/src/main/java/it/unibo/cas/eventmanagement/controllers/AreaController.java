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
        if (areaDTO == null) {
            logger.error("area is null");
            return ResponseEntity.badRequest().body("Area is null");
        }
        Area area = areaService.createArea(areaDTO);
        if (area != null) {
            logger.info("area created");
            try {
                kubernetesOrchestrationService.deployAnalysisForArea(area.getName());
                logger.info("area pod created successfully");
                return ResponseEntity.ok().body("area created successfully");
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body(e.getMessage());
            }
        } else {
            logger.error("something went wrong in area creating");
            return ResponseEntity.internalServerError().body("Error occurs while creating area ");
        }
    }

    @GetMapping("area/{areaId}/capacity")
    public ResponseEntity<Integer> getAreaCapacity(@PathVariable String areaId) {
        if (areaId == null) {
            logger.error("areaId is null");
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(areaService.getCapacity(areaId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("area/{areaId}/m2")
    public ResponseEntity<Double> getM2(@PathVariable String areaId) {
        if (areaId == null) {
            logger.error("areaId is null");
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(areaService.getM2(areaId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("area/{areaId}")
    public ResponseEntity<String> updateArea(@PathVariable Long areaId, @RequestBody AreaDTO areaDTO) {
        // TODO
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/area/{areaId}")
    public ResponseEntity<String> deleteArea(@PathVariable String areaId) throws UnsupportedOperationException {
        Area area = areaService.getArea(areaId);
        if (area != null) {
            areaService.deleteArea(area);
            logger.info("area deleted successfully");
            try {
                kubernetesOrchestrationService.removeAnalysisForArea(area.getName());
                logger.info("area pod removed successfully");
                return ResponseEntity.ok().body("area removed successfully");
            } catch (RuntimeException e) {
                return ResponseEntity.internalServerError().body(e.getMessage());
            }
        } else {
            logger.error("area not found");
            return ResponseEntity.badRequest().body("Area to delete not found");
        }
    }
}