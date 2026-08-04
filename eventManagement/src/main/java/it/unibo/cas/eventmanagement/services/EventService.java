package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.EventDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Event;
import it.unibo.cas.eventmanagement.repositories.EventRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EventService {
    @Autowired
    private EventRepository eventRepository;

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
        eventRepository.deleteAll();
    }
}