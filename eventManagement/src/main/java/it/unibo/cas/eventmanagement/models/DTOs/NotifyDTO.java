package it.unibo.cas.eventmanagement.models.DTOs;

import it.unibo.cas.eventmanagement.models.entities.Alert;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotifyDTO {
    private String message;
    private Alert alert;
    private Priority priority;
}
