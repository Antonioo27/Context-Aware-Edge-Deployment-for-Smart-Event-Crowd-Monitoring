package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import it.unibo.cas.eventmanagement.models.DTOs.ManualAlertDTO;
import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.models.enums.AlertType;
import it.unibo.cas.eventmanagement.repositories.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * Service responsible for managing and persisting crowd safety alerts in the central backend.
 *
 * Responsibilities:
 * - Persists alerts triggered automatically by Edge analysis workers (Slow-Path REST ingestion).
 * - Stores proactive predictive alerts produced by the backend crowd forecasting engine.
 * - Handles manual hazard reports submitted by field operators, resolving their GPS coordinates
 *   to an event area using PostGIS spatial queries.
 * - Serves as the central repository for historical audit and dashboard reporting.
 */
@Slf4j
@Service
public class AlertService {
    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AreaService areaService;

    public Alert addAlert(AlertDTO alertDTO) {
        Alert alert = alertRepository.save(
                Alert.builder()
                        .areaId(alertDTO.getArea_id())
                        .ts(alertDTO.getTs())
                        .alertType(alertDTO.getAlertType() != null ? alertDTO.getAlertType() : AlertType.AUTOMATIC)
                        .cause(alertDTO.getCause())
                        .build()
        );
        log.info("Added alert: {}", alert);
        return alert;
    }

    public Alert addAlert(ManualAlertDTO manualAlertDTO) {
        String areaId = areaService.getAreaByCoords(manualAlertDTO.getLon(), manualAlertDTO.getLat());
        Alert alert = alertRepository.save(
                Alert.builder()
                        .areaId(areaId)
                        .ts(manualAlertDTO.getTs())
                        .alertType(AlertType.MANUAL)
                        .cause(manualAlertDTO.getCause())
                        .build()
        );
        log.info("Added a manual alert: {}", manualAlertDTO);
        return alert;
    }

    public void deleteAlerts() {
        alertRepository.deleteAll();
    }
}
