package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
}
