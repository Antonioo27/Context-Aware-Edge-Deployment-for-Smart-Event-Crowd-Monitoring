package it.unibo.cas.eventmanagement.models.enums;

public enum State {
    NONE(1, 1.0),
    LOW(2, 1.0),
    MEDIUM(3, 1.0),
    HIGH(4, 1.5),
    CRITICAL(5, 2.5);

    private final int rank;
    private final double criticalityFactor;

    State(int rank, double criticalityFactor) {
        this.rank = rank;
        this.criticalityFactor = criticalityFactor;
    }

    public int getRank() {
        return rank;
    }

    public double getCriticalityFactor() {
        return criticalityFactor;
    }

    public static int getRankOrDefault(State state) {
        return state != null ? state.getRank() : NONE.getRank();
    }

    public static double getCriticalityFactorOrDefault(State state) {
        return state != null ? state.getCriticalityFactor() : 1.0;
    }
}