package it.unibo.cas.eventmanagement.orchestration;

import org.springframework.stereotype.Component;

import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.models.enums.State;

@Component
public class CostCalculator {

    private static final double CLOUD_WAN_PENALTY = 80.0;

    // Costanti per le latenze
    private static final double L_EXIT_EDGE_MS = 40.0; // Da Edge a Cloud DB
    private static final double L_EXIT_CLOUD_MS = 1.0;  // Co-locato sul Cloud

    /**
     * calcolo costo totale di un piazzamento Pod dell'area A sul nodo N-
     * Costo = L_ingresso(A,N) * W(Area) + L_uscita(A,N) + PenalitàCarico
     */
    public double calculateCost(Area area, String targetNodeId, double lIngressoMs, int currentPodsOnNode, State currentState, Boolean isTargetNodeOverloaded) {
        
        boolean isCloud = "node-cloud".equals(targetNodeId);

        // Calcolo latenza uscita
        double lUscita = isCloud ? L_EXIT_CLOUD_MS : L_EXIT_EDGE_MS;

        // Calcolo peso area W(A) = PriorityWeight * CriticalityFactor
        double priorityWeight = getPriorityWeight(area.getPriority());
        double criticalityFactor = getCriticalityFactor(currentState);
        double weight = priorityWeight * criticalityFactor;

        // Penalità carico, se nodo edge ospita già Pod
        double loadPenalty = 0.0;
        if (!isCloud) {
            loadPenalty = currentPodsOnNode * 15.0;
        } else {
            loadPenalty = CLOUD_WAN_PENALTY;
        }

        if (isTargetNodeOverloaded) {
            loadPenalty += 150;
        }

        return (lIngressoMs * weight) + lUscita + loadPenalty;

    }

    private double getPriorityWeight(Priority priority) {
        if (priority == null) return 1.0;
        return switch (priority) {
            case VERY_HIGH -> 4.0;
            case HIGH -> 3.0;
            case MEDIUM -> 2.0;
            case LOW -> 1.0;
            case VERY_LOW -> 0.5;
        };
    }

    private double getCriticalityFactor(State state) {
        if (state == null) return 1.0;
        return switch (state) {
            case CRITICAL -> 2.5;
            case HIGH -> 1.5;
            case MEDIUM, LOW, NONE -> 1.0;
        };
    }

}
