package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.AreaDTO;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Event;
import it.unibo.cas.eventmanagement.repositories.AreaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AreaService {
    @Autowired
    private AreaRepository areaRepository;
    @Autowired
    private EventService eventService;

    @Transactional
    public Area createArea(AreaDTO areaDTO) {
        if (areaDTO == null)
            throw new IllegalArgumentException("area is null");
        if (areaDTO.getCapacity() <= 0)
            throw new IllegalArgumentException("capacity is negative");

        Area area = Area.builder()
                .name(areaDTO.getName())
                .boundary(areaDTO.getBoundary())
                .capacity(areaDTO.getCapacity())
                .build();
        Event event = eventService.getEvent();
        event.addArea(area);
        return areaRepository.save(area);
    }

    public int getCapacity(String areaName) {
        Area area = areaRepository.getAreaByName(areaName);
        return area.getCapacity();
    }

    public void deleteArea(String areaName) {
        Area area = areaRepository.getAreaByName(areaName);
        areaRepository.delete(area);
    }

    public void deleteArea(Area area) {
        areaRepository.delete(area);
    }

    public Area getArea(String areaName) {
        return areaRepository.getAreaByName(areaName);
    }
}