package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Migration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationRepository extends JpaRepository<Migration, Long> {
    
    // Recupera lo storico migrazioni ordinato per tempo decrescente
    List<Migration> findAllByOrderByTimestampDesc();

    // Recupera lo storico migrazioni di una specifica area
    List<Migration> findByAreaIdOrderByTimestampDesc(String areaId);
       
}