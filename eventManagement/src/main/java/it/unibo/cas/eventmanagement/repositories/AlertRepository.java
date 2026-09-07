package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Alert;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import it.unibo.cas.eventmanagement.models.enums.AlertType;;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Conta solo gli alert che corrispondono ai tipi specificati (MANUAL, AUTOMATIC)
    long countByAlertTypeIn(List<AlertType> types);

    // In alternativa, esclude esplicitamente un tipo specifico (PREDICTION)
    long countByAlertTypeNot(AlertType excludedType);
}