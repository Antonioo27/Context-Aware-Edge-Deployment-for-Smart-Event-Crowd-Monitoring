package it.unibo.cas.eventmanagement.models.entities;

import jakarta.persistence.*;
import lombok.*;
import it.unibo.cas.eventmanagement.models.enums.UserType;
import it.unibo.cas.eventmanagement.models.enums.Priority;
@Entity
@Table(name = "notifications")
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString

public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Enumerated(EnumType.STRING)
    private UserType targetUser;

    private String message;

    @Enumerated(EnumType.STRING)
    private Priority priority;
}
