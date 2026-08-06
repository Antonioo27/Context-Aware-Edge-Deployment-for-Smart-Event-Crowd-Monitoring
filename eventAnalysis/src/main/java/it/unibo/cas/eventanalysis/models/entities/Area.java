package it.unibo.cas.eventanalysis.models.entities;

/**
 * Represent the monitored area by this specific analysis service instance.
 * This is a record, so it's immutabile.
 */
public record Area(String id, int capacity, double m2) {

    public Area(String id, int capacity, double m2) {
        this.id = id;
        this.capacity = capacity;
        this.m2 = m2;
    }
}
