package it.unibo.cas.eventmanagement.services;

import it.unibo.cas.eventmanagement.models.DTOs.AlertDTO;
import it.unibo.cas.eventmanagement.models.DTOs.NotifyDTO;
import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.entities.Notification;
import it.unibo.cas.eventmanagement.models.enums.AreaType;
import it.unibo.cas.eventmanagement.models.enums.UserType;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NotifyService {
    @Autowired
    private AreaService areaService;

    @Autowired
    private NotificationRepository notificationRepository;

    public void notifyAutomaticAlert(Alert alert) {
        Area area = areaService.getArea(alert.getAreaId());
        String message = "Alta densità di persone nell'area " + alert.getAreaId();
        
        String userMessage = "";
        String operatorMessage = "";
        String organizerMessage = "";

        switch (area.getType()) {
            case PEAK_ATTRACTION:
            case SUSTAINED_ATTRACTION:
                userMessage = message + ". Si prega di passare altrove.";
                operatorMessage = message + ". Favorire il flusso di persone verso altre aree.";
                organizerMessage = message + ". Spostare operatori";
                break;
            case ENTRANCE:
                userMessage = message + ". Si prega di rispettare la coda e i controlli.";
                operatorMessage = message + ". Fa rispettare la fila agli utenti.";
                organizerMessage = message + ". Valutare l'assegnazione di maggiori operatori alla sezione di Entrata all'Evento";
                break;
            case EXIT:
                userMessage = message + ". Si prega di non ammassarsi e non spingere.";
                operatorMessage = message + ". Indirizzare gli utenti verso altre uscite se disponibili";
                organizerMessage = message + ". Valutare l'apertura di un'ulteriore uscita";
                break;
            case GENERIC:
                userMessage = message + ". Per ora vi consigliamo di visitare altre aree.";
                operatorMessage = message + ". Indirizzare gli utenti verso altre aree dell'evento";
                organizerMessage = message + ". Valutare se spostare l'attenzione altrove";
                break;
            case TRANSIT:
                userMessage = message + ". Per ora vi consigliamo di utilizzare altre aree di transito.";
                operatorMessage = message + ". Indirizzare gli utenti verso altre aree aree di transito";
                organizerMessage = message + ". Valutare se aprire un passaggio ulteriore";
                break;
            default:
                break;
        }

        if (!userMessage.isEmpty()) {
            createNotification(alert, UserType.USER, userMessage);
            createNotification(alert, UserType.OPERATOR, operatorMessage);
            createNotification(alert, UserType.ORGANIZER, organizerMessage);
        }
    }

    public void notifyManualAlert(Alert alert) {
        String baseMessage = "Allerta manuale segnalata";
        if (alert.getAreaId() != null && !alert.getAreaId().isEmpty()) {
            baseMessage += " in area " + alert.getAreaId();
        }
        if (alert.getCause() != null && !alert.getCause().isEmpty()) {
            baseMessage += " (causa: " + alert.getCause() + ")";
        }

        createNotification(alert, UserType.OPERATOR, baseMessage + ". Si prega di verificare la situazione.");
        createNotification(alert, UserType.ORGANIZER, baseMessage + ". Valutare possibili interventi o riorganizzazioni.");
    }

    private void createNotification(Alert alert, UserType targetUser, String message) {
        Notification notification = Notification.builder()
                .alert(alert)
                .targetUser(targetUser)
                .message(message)
                .build();
        notificationRepository.save(notification);
    }

    public List<NotifyDTO> getNotifyByUser(UserType userType) {
        List<Notification> list = notificationRepository.findByTargetUser(userType);
        List<NotifyDTO> listDTO = new ArrayList<>();
        for (Notification notification : list) {
            Priority priority = null;
            if (userType == UserType.OPERATOR || userType == UserType.ORGANIZER) {
                try {
                    if (notification.getAlert() != null && notification.getAlert().getAreaId() != null) {
                        Area area = areaService.getArea(notification.getAlert().getAreaId());
                        if (area != null) {
                            priority = area.getPriority();
                        }
                    }
                } catch (Exception e) {
                    // Ignore missing area
                }
            }
            
            listDTO.add(NotifyDTO.builder()
                    .message(notification.getMessage())
                    .alert(notification.getAlert())
                    .priority(priority)
                    .build());
        }
        return listDTO;
    }
}