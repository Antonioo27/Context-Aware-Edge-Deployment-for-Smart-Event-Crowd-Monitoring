package it.unibo.cas.eventmanagement.orchestration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.unibo.cas.eventmanagement.config.LatencyProperties;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.models.enums.State;

@Component
public class CostCalculator {

    @Autowired
    private LatencyProperties latencyConfig;

    private static final double CLOUD_WAN_PENALTY = 80.0;
    /**
     * calcolo costo totale di un piazzamento Pod dell'area A sul nodo N-
     * Costo = L_ingresso(A,N) * W(Area) + L_uscita(A,N) + PenalitàCarico
     */
    public double calculateCost(Area area, String targetNodeId, double lIngressoMs, int currentPodsOnNode, 
        State currentState, Boolean isTargetNodeOverloaded, Boolean nodeHostsCriticalArea) {
        
        boolean isCloud = "node-cloud".equals(targetNodeId);

        // Calcolo latenza uscita
        double lUscita = isCloud ? latencyConfig.getExitCloudMs() : latencyConfig.getExitEdgeMs();

        // Calcolo peso area W(A) = PriorityWeight * CriticalityFactor
        double priorityWeight = getPriorityWeight(area.getPriority());
        double criticalityFactor = getCriticalityFactor(currentState);
        double weight = priorityWeight * criticalityFactor;

        // Penalità carico, se nodo edge ospita già Pod
        double loadPenalty = 0.0;
        if (!isCloud) {
            loadPenalty = currentPodsOnNode * 3.0;
            
            // Prevenzione: se il nodo ospita un'area CRITICAL e quest'area non è CRITICAL,
            // applichiamo un extra costo per liberare preventivamente l'Edge
            if (nodeHostsCriticalArea && currentState != State.CRITICAL) {
                loadPenalty += 40.0;
            }
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
