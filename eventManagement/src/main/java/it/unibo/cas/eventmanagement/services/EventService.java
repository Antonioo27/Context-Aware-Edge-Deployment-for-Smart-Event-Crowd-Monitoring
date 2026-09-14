package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.EventDTO;
import it.unibo.cas.eventmanagement.models.entities.Event;
import it.unibo.cas.eventmanagement.orchestration.KubernetesOrchestrationService;
import it.unibo.cas.eventmanagement.repositories.EventRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing the root event lifecycle in the system.
 *
 * Architectural Role:
 * - Enforces a single-event model per running instance of the backend platform.
 * - Coordinates cascading deletions across all domain layers and infrastructure services.
 * - Serves as the root parent entity for spatial areas, crowd analyses, and safety alerts.
 */
@Service
public class EventService {
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private AlertService alertService;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private AreaService areaService;
    @Autowired
    private NotifyService notifyService;
    @Autowired
    private KubernetesOrchestrationService kubernetesOrchestrationService;

    public Event getEvent() {
        return eventRepository.findAll().getFirst();
    }

    private Boolean assertZeroEvents() {
        return eventRepository.count() == 0;
    }

    @Transactional
    public Event createEvent(EventDTO eventDTO) {
        if(!assertZeroEvents()) {
            throw new IllegalArgumentException("One event already exists");
        }
        if(eventDTO == null)
            throw new IllegalArgumentException("event is null");

        Event event = Event.builder()
                .name(eventDTO.getName())
                .description(eventDTO.getDescription())
                .city(eventDTO.getCity())
                .location(eventDTO.getLocation())
                .build();

        return eventRepository.save(event);
    }

    public void deleteEvent() {
        notifyService.deleteNotifications();
        alertService.deleteAlerts();
        analysisService.deleteAnalysis();
        areaService.deleteAreas();
        eventRepository.deleteAll();
        kubernetesOrchestrationService.removeAllDeployAnalysis();
    }
}