package it.unibo.cas.eventmanagement.models.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Data
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private int  id;
    private String name;
    private String description;
    private String location;
    private String city;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Area> areas = new ArrayList<>();

    public void addArea(Area area) {
        if (this.areas == null) {
            this.areas = new ArrayList<>();
        }
        this.areas.add(area);
        area.setEvent(this);
    }
}
