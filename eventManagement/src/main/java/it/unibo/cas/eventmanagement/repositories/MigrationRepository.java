package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Migration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationRepository extends JpaRepository<Migration, Long> {
    
    List<Migration> findAllByOrderByTimestampDesc();

    List<Migration> findByAreaIdOrderByTimestampDesc(String areaId);
       
}