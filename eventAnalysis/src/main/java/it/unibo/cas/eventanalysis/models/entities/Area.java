package it.unibo.cas.eventanalysis.models.entities;

/**
 * Represent the monitored area by this specific analysis service instance.
 * This is a record, so it's immutabile.
 */
public record Area(String id, int capacity) {

    public Area(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }
}
