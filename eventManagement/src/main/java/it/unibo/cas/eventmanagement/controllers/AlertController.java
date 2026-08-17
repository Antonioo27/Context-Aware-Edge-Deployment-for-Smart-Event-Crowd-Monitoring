package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import it.unibo.cas.eventmanagement.models.DTOs.ManualAlertDTO;
import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.services.AlertService;
import it.unibo.cas.eventmanagement.services.NotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("api/event/area")
public class AlertController {
    @Autowired
    private AlertService alertService;

    @Autowired
    private NotifyService notifyService;

    @PostMapping("/alert")
    public ResponseEntity<String> receiveAlert(@RequestBody AlertDTO alertDTO) {

        if (alertDTO == null) {
            log.error("ALERT RICEVUTO MA NULL");
            return ResponseEntity.badRequest().body("Alert object is null");
        }

        log.warn("[ALERT RICEVUTO] Area: {} | Ora: {} | Causa: {}",
                alertDTO.getArea_id(),
                alertDTO.getTs(),
                alertDTO.getCause());
        try {
            Alert alert = alertService.addAlert(alertDTO);
            notifyService.notifyAutomaticAlert(alert);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        return ResponseEntity.ok("Alert ricevuto con successo dal Backend");
    }

    @PostMapping("/manual-alert")
    public ResponseEntity<String> receiveManualAlert(@RequestBody ManualAlertDTO manualAlertDTO) {
        if (manualAlertDTO == null) {
            log.error("ALERT MANUALE RICEVUTO MA NULL");
            return ResponseEntity.badRequest().body("Alert object is null");
        }


        log.warn("[ALERT MANUALE RICEVUTO] Lat: {} | Lon: {} | Ora: {} | Causa: {}",
                manualAlertDTO.getLat(),
                manualAlertDTO.getLon(),
                manualAlertDTO.getTs(),
                manualAlertDTO.getCause());
        try {
            Alert alert = alertService.addAlert(manualAlertDTO);
            notifyService.notifyManualAlert(alert);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        return ResponseEntity.ok("Alert ricevuto con successo dal Backend");
    }

}
