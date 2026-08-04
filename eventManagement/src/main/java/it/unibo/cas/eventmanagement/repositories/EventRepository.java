package it.unibo.cas.eventmanagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import it.unibo.cas.eventmanagement.models.entities.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, Integer> {
    Event findByName(String name);
    Event findById(int id);
}