package it.unibo.cas.eventmanagement.orchestration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.unibo.cas.eventmanagement.config.LatencyProperties;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.models.enums.State;

/**
 * Architectural Role:
 * Evaluates the placement cost of running an event analysis worker pod on a candidate compute node.
 * It serves as the analytical scoring engine used by the context-aware control loop to make
 * informed migration and scheduling decisions across Edge and Cloud tiers.
 *
 * Operational Caracteristics:
 * - Edge-First Anchoring: Amplifies ingress latency using area priority and crowd risk factors,
 *   strongly anchoring critical or high-density areas to their closest local Edge node.
 * - Resource and Congestion Protection: Adds penalties for co-located pods, CPU overload conditions,
 *   and cloud WAN traversal to avoid resource starvation and protect high-risk services.
 */
@Component
public class CostCalculator {

    @Autowired
    private LatencyProperties latencyConfig;

    private static final double CLOUD_WAN_PENALTY = 80.0;
    
    /**
     * Computes the scalar placement cost for an area on a target node using network latencies,
     * crowd risk weights, and infrastructure load penalties.
     *
     * Formula:
     * Cost(A, N) = (IngressLatency * AreaWeight) + ExitLatency + LoadPenalty
     *
     * @param area the monitored event area entity
     * @param targetNodeId the identifier of the candidate compute node (e.g., node-edge-1, node-cloud)
     * @param lIngressoMs network ingress delay from sensors/probes to the node in milliseconds
     * @param currentPodsOnNode number of analysis worker pods currently assigned to the node
     * @param currentState current crowd danger state of the area (e.g., CRITICAL, HIGH, LOW)
     * @param isTargetNodeOverloaded flag indicating whether candidate node CPU usage exceeds safe limits (>75%)
     * @param nodeHostsCriticalArea flag indicating whether the candidate node already hosts a CRITICAL area
     * @return the calculated placement cost score
     */
    public double calculateCost(Area area, String targetNodeId, double lIngressoMs, int currentPodsOnNode, 
        State currentState, Boolean isTargetNodeOverloaded, Boolean nodeHostsCriticalArea) {
        
        boolean isCloud = "node-cloud".equals(targetNodeId);

        double lUscita = isCloud ? latencyConfig.getExitCloudMs() : latencyConfig.getExitEdgeMs();

        double priorityWeight = Priority.getWeightOrDefault(area.getPriority());
        double criticalityFactor = State.getCriticalityFactorOrDefault(currentState);
        double weight = priorityWeight * criticalityFactor;

        double loadPenalty = 0.0;
        if (!isCloud) {
            loadPenalty = currentPodsOnNode * 3.0;
            
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
}
