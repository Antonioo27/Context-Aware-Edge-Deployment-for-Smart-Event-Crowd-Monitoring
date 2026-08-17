package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import it.unibo.cas.eventmanagement.models.DTOs.ManualAlertDTO;
import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.repositories.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
                        .cause(manualAlertDTO.getCause())
                        .build()
        );
        log.info("Added a manual alert: {}", manualAlertDTO);
        return alert;
    }
}
