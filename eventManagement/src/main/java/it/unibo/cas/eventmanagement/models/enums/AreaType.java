package it.unibo.cas.eventmanagement.models.enums;

/**
 * Rappresenta la tipologia comportamentale dell'area per guidare 
 * le matrici di transizione del simulatore e le logiche context-aware.
 */
public enum AreaType {
    ENTRANCE,
    EXIT,
    TRANSIT,
    PEAK_ATTRACTION,
    SUSTAINED_ATTRACTION,
    GENERIC
}
