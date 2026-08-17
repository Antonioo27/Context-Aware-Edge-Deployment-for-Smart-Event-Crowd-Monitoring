package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.Notification;
import it.unibo.cas.eventmanagement.models.enums.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByTargetUser(UserType targetUser);
}
