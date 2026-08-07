package it.unibo.cas.eventmanagement.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import it.unibo.cas.eventmanagement.models.entities.Node;

@Repository
public interface NodeRepository extends JpaRepository<Node, String> {
    
    /**
     * Calcola la distanza geodesica in metri tra la superficie/bordo dell'Area (Polygon)
     * e la posizione del Nodo (Point) usando il cast a geography di PostGIS (SRID 4326).
     */
    @Query(value = """
            SELECT a.name AS areaName, n.id AS nodeId, 
                   ST_Distance(a.boundary::geography, n.location::geography) AS distanceMeters
            FROM areas a, nodes n
            """, nativeQuery = true)
    List<Object[]> findAllAreaNodeDistances();   
    
    /**
     * Calcola la distanza in metri tra un'area e un nodo specifici.
     */
    @Query(value = """
            SELECT ST_Distance(a.boundary::geography, n.location::geography) AS distanceMeters
            FROM areas a, nodes n
            WHERE a.name = :areaName AND n.id = :nodeId
            """, nativeQuery = true)
    Double findDistanceBetweenAreaAndNode(@Param("areaName") String areaName, @Param("nodeId") String nodeId);
}
