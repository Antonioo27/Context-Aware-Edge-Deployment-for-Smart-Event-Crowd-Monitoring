package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.DTOs.EventDTO;
import it.unibo.cas.eventmanagement.models.entities.Event;
import it.unibo.cas.eventmanagement.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/event")
public class EventController {
    private static final Logger logger = LoggerFactory.getLogger(EventController.class);

    @Autowired
    private EventService eventService;

    @PostMapping()
    public ResponseEntity<String> createEvent(@RequestBody EventDTO eventDTO) {
        try {
            if (eventDTO == null) {
                logger.error("event is null");
                return ResponseEntity.badRequest().body("Event is null");
            }
            if (eventService.createEvent(eventDTO) != null) {
                logger.info("event created");
                return ResponseEntity.ok("Event successfully created");
            } else {
                logger.error("something went wrong while creating event");
                return ResponseEntity.internalServerError().body("Error occurs while creating event ");
            }
        } catch (Exception e) {
            logger.error("something went wrong");
            return ResponseEntity.internalServerError().body("Error occurs");
        }
    }

    @DeleteMapping()
    public ResponseEntity<String> deleteEvent() {
        try {
            eventService.deleteEvent();
            logger.info("event deleted");
            return ResponseEntity.ok("Event successfully deleted");
        } catch (Exception e) {
            logger.error("something went wrong  while deleting event: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error occurs while deleting event ");
        }
    }

    @GetMapping()
    public ResponseEntity<Event> getEvent(){
        try {
            return ResponseEntity.ok(eventService.getEvent());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}