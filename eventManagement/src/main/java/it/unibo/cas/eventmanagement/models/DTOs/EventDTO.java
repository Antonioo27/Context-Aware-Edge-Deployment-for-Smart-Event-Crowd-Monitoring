package it.unibo.cas.eventmanagement.models.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {
    private String name;
    private String description;
    private String location;
    private String city;
}
