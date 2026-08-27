package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.AreaDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Event;
import it.unibo.cas.eventmanagement.models.enums.State;
import it.unibo.cas.eventmanagement.repositories.AreaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import it.unibo.cas.eventmanagement.exception.ResourceNotFoundException;

@Service
public class AreaService {

    private static final Logger logger = LoggerFactory.getLogger(AreaService.class);

    @Autowired
    private AreaRepository areaRepository;
    @Autowired
    @Lazy
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
                .state(State.NONE)
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
        Double area = areaRepository.getAreaSizeInSquareMeters(areaId);
        return area != null ? area : 0.0;
    }

    public List<Area> getAllAreas() {
        return areaRepository.findAll();
    }

    @Transactional
    public Area updateArea(String areaName, AreaDTO areaDTO) {

        if (areaDTO == null) {
            throw new IllegalArgumentException("I dati per l'aggiornamento dell'area non possono essere nulli");
        }

        Area area = getArea(areaName);

        if (areaDTO.getCapacity() > 0) {
            area.setCapacity(areaDTO.getCapacity());
        }
        if (areaDTO.getBoundary() != null) {
            area.setBoundary(areaDTO.getBoundary());
        }
        if (areaDTO.getType() != null) {
            area.setType(areaDTO.getType());
        }
        if (areaDTO.getPriority() != null) {
            area.setPriority(areaDTO.getPriority());
        }
        if (areaDTO.getName() != null && !areaDTO.getName().trim().isEmpty()) {
            area.setName(areaDTO.getName());
        }

        Area updated = areaRepository.save(area);
        logger.info("Area '{}' aggiornata con successo: nuova capacità={}, priorità={}",
                areaName, updated.getCapacity(), updated.getPriority());
        return updated;

    }

    public String getAreaByCoords(double lon, double lat) {
        return areaRepository.getAreaByCoords(lon, lat);
    }

    public void setAreaState(State state, String areaName) {
        Area area = areaRepository.getAreaByName(areaName);
        area.setState(state);
        areaRepository.save(area);
    }

    public void deleteAreas() {
        areaRepository.deleteAll();
    }
}