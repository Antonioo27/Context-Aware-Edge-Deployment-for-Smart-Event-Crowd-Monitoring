package it.unibo.cas.eventmanagement.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import it.unibo.cas.eventmanagement.models.entities.Node;

@Repository
public interface NodeRepository extends JpaRepository<Node, String> {
    
    @Query(value = """
            
            """)
    List<Object[]> findAllAreaNodeDistances();   
    
    @Query(value = """
            
            """)
    Double findDistanceBetweenAreaAndNode(@Param("areaname") String areaName, @Param("nodeId") String nodeId);
}
