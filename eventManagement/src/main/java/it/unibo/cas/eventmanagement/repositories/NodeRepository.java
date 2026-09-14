package it.unibo.cas.eventmanagement.repositories;

import java.util.List;
import java.util.Optional;

import org.locationtech.jts.geom.Polygon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import it.unibo.cas.eventmanagement.models.entities.Node;

@Repository
public interface NodeRepository extends JpaRepository<Node, String> {
    
    /**
     * Computes geodesic distances between all areas and all registered nodes.
     * Uses a cross join to produce a complete distance matrix.
     */
    @Query(value = """
            SELECT a.name AS areaName, n.id AS nodeId, 
                   ST_Distance(a.boundary::geography, n.location::geography) AS distanceMeters
            FROM areas a, nodes n
            """, nativeQuery = true)
    List<Object[]> findAllAreaNodeDistances();   
    
    /**
     * Computes the distance in meters between a specific area and a single node.
     */
    @Query(value = """
            SELECT ST_Distance(a.boundary::geography, n.location::geography) AS distanceMeters
            FROM areas a, nodes n
            WHERE a.name = :areaName AND n.id = :nodeId
            """, nativeQuery = true)
    Double findDistanceBetweenAreaAndNode(@Param("areaName") String areaName, @Param("nodeId") String nodeId);

    /**
     * Finds the nearest healthy Edge node to the provided area boundary.
     * Enforces the 4326 SRID on the boundary to prevent mixed SRID exceptions.
     */
    @Query(value = """
                    SELECT n.id FROM nodes n
                    WHERE n.type = 'EDGE'
                    ORDER BY ST_Distance(n.location::geography, ST_SetSRID(:boundary, 4326)::geography) 
                    ASC LIMIT 1
                    """, nativeQuery = true)
    Optional<String> findClosestEdgeNodeId(@Param("boundary") Polygon boundary);
}
