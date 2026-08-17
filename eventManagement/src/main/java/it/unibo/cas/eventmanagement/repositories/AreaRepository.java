package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    Area getAreaByName(String name);

    @Query(value = "SELECT ST_Area(boundary::geography) FROM areas WHERE name = :name", nativeQuery = true)
    Double getAreaSizeInSquareMeters(@Param("name") String name);

    @Query(value = "SELECT name FROM areas WHERE ST_Contains(boundary, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)) LIMIT 1", nativeQuery = true)
    String getAreaByCoords(@Param("lon") double lon, @Param("lat") double lat);

}
