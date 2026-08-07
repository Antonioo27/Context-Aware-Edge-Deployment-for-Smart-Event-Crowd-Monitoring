package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.AreaDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Event;
import it.unibo.cas.eventmanagement.repositories.AreaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.unibo.cas.eventmanagement.exception.ResourceNotFoundException;

@Service
public class AreaService {

    private static final Logger logger = LoggerFactory.getLogger(AreaService.class);

    @Autowired
    private AreaRepository areaRepository;
    @Autowired
    private EventService eventService;

    @Transactional
    public Area createArea(AreaDTO areaDTO) {
        if (areaDTO == null) {
            throw new IllegalArgumentException("I dati dell'area non possono essere nulli");
        }
        if (areaDTO.getName() == null || areaDTO.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Il nome dell'area è obbligatorio");
        }
        if (areaDTO.getCapacity() <= 0) {
            throw new IllegalArgumentException("La capacità dell'area deve essere maggiore di zero");
        }

        Area area = Area.builder()
                .name(areaDTO.getName())
                .boundary(areaDTO.getBoundary())
                .capacity(areaDTO.getCapacity())
                .type(areaDTO.getType())
                .priority(areaDTO.getPriority())
                .build();

        Event event = eventService.getEvent();
        event.addArea(area);
        
        Area saved = areaRepository.save(area);
        logger.info("Area creata con successo: name={}, capacity={}", saved.getName(), saved.getCapacity());
        return saved;
    }

    public int getCapacity(String areaName) {
        Area area = areaRepository.getAreaByName(areaName);
        return area.getCapacity();
    }

    @Transactional
    public void deleteArea(String areaName) {
        Area area = areaRepository.getAreaByName(areaName);
        deleteArea(area);
    }

    @Transactional
    public void deleteArea(Area area) {
        areaRepository.delete(area);
    }

    public Area getArea(String areaName) {
        if (areaName == null || areaName.trim().isEmpty()) {
            throw new IllegalArgumentException("Il nome dell'area non può essere vuoto");
        }
        Area area = areaRepository.getAreaByName(areaName);
        if (area == null) {
            throw new ResourceNotFoundException("Area con nome '" + areaName + "' non trovata");
        }
        return area;
    }

    public Double getM2(String areaId) {
        // TODO calcolare l'area tramite postgis.
        Area area = getArea(areaId);
        return 0.0;
    }

    public List<Area> getAllAreas() {
        return areaRepository.findAll();
    }
}