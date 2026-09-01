package it.unibo.cas.eventmanagement.models.enums;

public enum Priority {
    VERY_LOW(1, 0.5),
    LOW(2, 1.0),
    MEDIUM(3, 2.0),
    HIGH(4, 3.0),
    VERY_HIGH(5, 4.0);

    private final int rank;
    private final double weight;

    Priority(int rank, double weight) {
        this.rank = rank;
        this.weight = weight;
    }

    public int getRank() {
        return rank;
    }

    public double getWeight() {
        return weight;
    }

    public static int getRankOrDefault(Priority priority) {
        return priority != null ? priority.getRank() : MEDIUM.getRank();
    }

    public static double getWeightOrDefault(Priority priority) {
        return priority != null ? priority.getWeight() : 1.0;
    }
}