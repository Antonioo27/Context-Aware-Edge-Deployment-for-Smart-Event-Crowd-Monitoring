package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AreaRepository extends JpaRepository<Area, Integer> {

    Area getAreaByName(String name);

    @org.springframework.data.jpa.repository.Query(value = "SELECT ST_Area(boundary::geography) FROM areas WHERE name = :name", nativeQuery = true)
    Double getAreaSizeInSquareMeters(@org.springframework.data.repository.query.Param("name") String name);
}