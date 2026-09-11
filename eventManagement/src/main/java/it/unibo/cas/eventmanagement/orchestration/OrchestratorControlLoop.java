package it.unibo.cas.eventmanagement.orchestration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import it.unibo.cas.eventmanagement.controllers.OrchestrationController;
import it.unibo.cas.eventmanagement.models.entities.Area;
import it.unibo.cas.eventmanagement.models.enums.OrchestrationPolicy;
import it.unibo.cas.eventmanagement.models.enums.Priority;
import it.unibo.cas.eventmanagement.models.enums.State;
import it.unibo.cas.eventmanagement.repositories.AreaRepository;
import it.unibo.cas.eventmanagement.services.MigrationService;
import it.unibo.cas.eventmanagement.services.NodeService;

/**
 * Architectural Role:
 * Central orchestration control loop for the event monitoring platform.
 * It implements a periodic Monitor, Analyze, Plan, Execute control cycle that balances
 * event analysis worker pods across Edge and Cloud compute nodes.
 *
 * Operational Characteristics:
 * - Periodic Scheduling: Executes every 10 seconds to observe node health, CPU loads, and area states.
 * - Multi-Policy Support: Supports CLOUD_ONLY, STATIC, and CONTEXT_AWARE policies.
 * - Fault Tolerance: Instantly relocates pods away from cordoned nodes, bypassing hysteresis.
 * - Thrashing Prevention: Enforces an anti-flapping hysteresis threshold and a per-area migration cooldown.
 * - Priority-Based Load Shedding: Evacuates lower-priority workloads first during CPU saturation
 *   to preserve local low-latency Edge resources for high-risk critical areas.
 */
@Component
public class OrchestratorControlLoop {
    
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorControlLoop.class);

    private static final double ISTERESI_THRESHOLD = 20.0;

    @Autowired
    private OrchestrationController orchestrationController;

    @Autowired
    private CostCalculator costCalculator;

    @Autowired
    private KubernetesOrchestrationService kubernetesOrchestrationService;

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private NodeService nodeService;

    // Periodo di non migrazione: un'area non può rimigrare prima di 60 secondi
    private static final long MIGRATION_COOLDOWN_MS = 60_000;

    private static double CPU_OVERLOAD_THRESHOLD = 75.0;

    private final Map<String, Long> lastMigrationTimestamps = new HashMap<>();

    /**
     * Periodic control loop tick triggered every 10 seconds.
     * Evaluates active areas and delegates orchestration decisions to the active policy handler.
     */
    @Scheduled(fixedRate = 10000)
    public void tick() {
        OrchestrationPolicy currentPolicy = orchestrationController.getCurrentPolicyInternal();
        List<Area> areas = areaRepository.findAll();

        if (areas.isEmpty()) {
            return;
        }

        logger.info(" [CONTROL LOOP] Tick avviato. Politica attiva: {}", currentPolicy);

        if (currentPolicy == OrchestrationPolicy.CLOUD_ONLY) {
            handleCloudOnlyPolicy(areas);
            return;
        }

        if (currentPolicy == OrchestrationPolicy.CONTEXT_AWARE) {
            handleContextAwarePolicy(areas);
            return;
        }

        if (currentPolicy == OrchestrationPolicy.STATIC) {
            handleStaticPolicy(areas);
        }

    }

    /**
     * Executes the cloud-only policy by migrating all analysis worker pods to the central cloud node.
     *
     * @param areas list of monitored event areas to inspect and migrate
     */
    private void handleCloudOnlyPolicy(List<Area> areas) {
        for (Area area : areas) {
            String currentNode = kubernetesOrchestrationService.getCurrentNodeForArea(area.getName());
            if (!"node-cloud".equals(currentNode)) {
                logger.info(" [CLOUD-ONLY] Spostamento area '{}' da {} -> node-cloud", area.getName(), currentNode);
                boolean success = kubernetesOrchestrationService.migratePodToNode(area.getName(), "node-cloud");

                String sanitizedAreaId = area.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
                migrationService.recordMigration(
                    area.getName(),
                    "event-analysis-" + sanitizedAreaId,
                    currentNode != null ? currentNode : "unknown",
                    "node-cloud",
                    null,
                    null,
                    "Migrating to cloud",
                    success,
                    success ? null : "K8s patch failed"
                );
            }
        }
    }

    /**
     * Executes the static policy by assigning each area to its geographically nearest available node.
     * Uses PostGIS distance queries to locate the closest Edge node and provides a cloud fallback.
     *
     * @param areas list of monitored event areas to place
     */
    private void handleStaticPolicy(List<Area> areas) {
        Map<String, Boolean> nodeHealthMap = kubernetesOrchestrationService.getNodeStatusMap();
        List<String> availableNodes = new ArrayList<>();
    
        nodeHealthMap.forEach((nodeId, isReady) -> {
            if (Boolean.TRUE.equals(isReady)) {
                availableNodes.add(nodeId);
            } else {
                logger.warn(" [STATIC-FAILOVER] Nodo {} offline!", nodeId);
            }
        });

        if (availableNodes.isEmpty()) {
            logger.error(" [ORCHESTRATOR-STATIC] Nessun nodo K8s disponibile!");
            return;
        }
    
        for (Area area : areas) {
            String currentNode = kubernetesOrchestrationService.getCurrentNodeForArea(area.getName());
            
            String targetStaticNode = findStaticBestNode(area, availableNodes);

            if (targetStaticNode == null) {
                targetStaticNode = "node-cloud";
            }

            if (!targetStaticNode.equals(currentNode)) {
                logger.info(" [STATIC-POLICY] Assegnazione statica Area '{}': {} -> {}",
                        area.getName(), currentNode, targetStaticNode);
            
                boolean success = kubernetesOrchestrationService.migratePodToNode(area.getName(), targetStaticNode);

                String sanitizedAreaId = area.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
                migrationService.recordMigration(
                    area.getName(),
                    "event-analysis-" + sanitizedAreaId,
                    currentNode != null ? currentNode : "unknown",
                    targetStaticNode,
                    null,
                    null,
                    "Static nearest edge assignment",
                    success,
                    success ? null : "K8s patch failed"
                );
            }
        }
    }

    /**
     * Executes the context-aware placement algorithm.
     * Observes cluster readiness and CPU load, sorts areas by priority to enable selective load shedding,
     * checks migration cooldown timers, evaluates cost functions across nodes, and migrates pods
     * when savings exceed the hysteresis margin or when an emergency failover is required.
     *
     * @param areas list of monitored event areas to evaluate
     */
    private void handleContextAwarePolicy(List<Area> areas) {
        
        Map<String, Boolean> nodeHealthMap = kubernetesOrchestrationService.getNodeStatusMap();
        List<String> availableNodes = new ArrayList<>();

        nodeHealthMap.forEach((nodeId, isReady) -> {
            if(Boolean.TRUE.equals(isReady)) {
                availableNodes.add(nodeId);
            } else {
                logger.warn(" [FAILOVER] Nodo {} risulta NOT READY!", nodeId);
            }
        });

        if (availableNodes.isEmpty()) {
            logger.error(" [ORCHESTRATOR] Nessun nodo K8s disponibile!");
            return;
        }

        Map<String, Integer> nodePodCount = new HashMap<>();
        availableNodes.forEach(node -> nodePodCount.put(node, 0));

        Map<String, String> currentAssignments = new HashMap<>();
        Set<String> nodesWithCriticalAreas = new HashSet<>();

        for (Area area : areas) {
            String currentNode = kubernetesOrchestrationService.getCurrentNodeForArea(area.getName());
            currentAssignments.put(area.getName(), currentNode);
            if (nodePodCount.containsKey(currentNode)) {
                nodePodCount.put(currentNode, nodePodCount.get(currentNode) + 1);
            }
            if (area.getState() == State.CRITICAL && currentNode != null) {
                nodesWithCriticalAreas.add(currentNode);
            }
        }

        List<Area> sortedAreas = new ArrayList<>(areas);
        sortedAreas.sort(Comparator.comparingInt((Area a) -> Priority.getRankOrDefault(a.getPriority()))
                                   .thenComparingInt(a -> State.getRankOrDefault(a.getState())));

        Map<String, Double> nodeCpuMap = kubernetesOrchestrationService.getNodeCpuUsagePercentageMap();

        Set<String> nodesEvacuatedInThisTick = new HashSet<>();

        long now = System.currentTimeMillis();

        for (Area area : sortedAreas) {
            String currentNode = currentAssignments.get(area.getName());   
            boolean currentNodeIsHealthy = Boolean.TRUE.equals(nodeHealthMap.get(currentNode));
            Double currentCpu = nodeCpuMap.getOrDefault(currentNode, 0.0);
            boolean isCurrentNodeOverloaded = currentCpu > CPU_OVERLOAD_THRESHOLD;
            
            if (currentNodeIsHealthy) {
                long lastMigrated = lastMigrationTimestamps.getOrDefault(area.getName(), 0L);
                if (now - lastMigrated < MIGRATION_COOLDOWN_MS) {
                    logger.debug(" [COOLDOWN] Area '{}' in fase di stabilizzazione (migrata {}s fa). Skip.",
                            area.getName(), (now - lastMigrated) / 1000);
                    continue;
                }
            }

            State currentState = area.getState() != null ? area.getState() : State.NONE;

            boolean applyOverloadPenaltyOnCurrent = isCurrentNodeOverloaded && !nodesEvacuatedInThisTick.contains(currentNode);

            if (isCurrentNodeOverloaded && !nodesEvacuatedInThisTick.contains(currentNode)) {
                logger.warn(" [OVERLOAD] Il nodo '{}' ha un utilizzo CPU del {}% (Soglia: {}%)!",
                        currentNode, String.format("%.1f", currentCpu), CPU_OVERLOAD_THRESHOLD);
            }

            if (!currentNodeIsHealthy) {
                logger.warn(" [FAILOVER] Il nodo attuale '{}' per l'area '{}' è offline. Ricalcolo immediato!", currentNode, area.getName());
            }

            String bestNode = currentNode;
            double currentCost = Double.MAX_VALUE;

            if (currentNodeIsHealthy) {
                double lIngressoCurrent = nodeService.getIngressLatency(area, currentNode);
                int podsOnCurrentNode = Math.max(0, nodePodCount.getOrDefault(currentNode, 1) - 1);
                boolean currentNodeHasCritical = nodesWithCriticalAreas.contains(currentNode);

                currentCost = costCalculator.calculateCost(
                        area, currentNode, lIngressoCurrent, podsOnCurrentNode, 
                        currentState, applyOverloadPenaltyOnCurrent, currentNodeHasCritical
                );
            }
            double minCost = currentCost;

            for (String candidateNode : availableNodes) {
                if (candidateNode.equals(currentNode) && currentNodeIsHealthy) {
                    continue;
                }

                double lIngressoCandidate = nodeService.getIngressLatency(area, candidateNode);
                int targetLoad = nodePodCount.getOrDefault(candidateNode, 0) + 1;

                Double candidateCpu = nodeCpuMap.getOrDefault(candidateNode, 0.0);
                boolean isCandidateOverloaded = candidateCpu > CPU_OVERLOAD_THRESHOLD;
                boolean candidateHasCritical = nodesWithCriticalAreas.contains(candidateNode);

                double candidateCost = costCalculator.calculateCost(
                        area, candidateNode, lIngressoCandidate, targetLoad, 
                        currentState, isCandidateOverloaded, candidateHasCritical
                );

                if (!currentNodeIsHealthy || candidateCost < (currentCost - ISTERESI_THRESHOLD)) {
                    if (candidateCost < minCost) {
                        minCost = candidateCost;
                        bestNode = candidateNode;
                    }
                }
            }

            if (!bestNode.equals(currentNode)) {
                logger.info(" [ORCHESTRATOR] Migrazione decisa per Area '{}': {} -> {} (Costo attuale: {}, Nuovo costo: {})",
                        area.getName(), currentNode, bestNode, String.format("%.2f", currentCost), String.format("%.2f", minCost));
                        
                boolean success = kubernetesOrchestrationService.migratePodToNode(area.getName(), bestNode);
                
                String reason;
                if(!currentNodeIsHealthy) {
                    reason = String.format("Failover: Node '%s' is NOT READY / Offline", currentNode);
                } else if(isCurrentNodeOverloaded) {
                    reason = String.format("Overload Avoidance: Node '%s' CPU at %.1f%% (> %.0f%%)", 
                                           currentNode, currentCpu, CPU_OVERLOAD_THRESHOLD);
                } else {
                    double delta = currentCost - minCost;
                    reason = String.format("Context-Aware Cost Optimization (Cost: %.2f -> %.2f | Saving: %.2f)",
                                           currentCost, minCost, delta);
                }

                String sanitizedAreaId = area.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
                migrationService.recordMigration(
                    area.getName(),
                    "event-analysis-" + sanitizedAreaId,
                    currentNode != null ? currentNode : "unknown",
                    bestNode,
                    currentCost != Double.MAX_VALUE ? currentCost : null,
                    minCost,
                    reason,
                    success,
                    success ? null : "Failed to patch K8s deployment"
                );

                if (success) {

                    lastMigrationTimestamps.put(area.getName(), now);
                    if (nodePodCount.containsKey(currentNode)) {
                        nodePodCount.put(currentNode, nodePodCount.get(currentNode) - 1);
                    }
                    if (nodePodCount.containsKey(bestNode)) {
                        nodePodCount.put(bestNode, nodePodCount.get(bestNode) + 1);
                    }

                    if (isCurrentNodeOverloaded) {
                        nodesEvacuatedInThisTick.add(currentNode);
                    }
                }
            }
        
        }
    }

    /**
     * Identifies the node with the lowest geographic ingress latency for a given area.
     * Evaluates physical distances using PostGIS coordinates without considering crowd dynamics or CPU load.
     *
     * @param area the monitored area entity
     * @param availableNodes list of healthy candidate node identifiers
     * @return identifier of the closest node, or null if no nodes are available
     */
    private String findStaticBestNode(Area area, List<String> availableNodes) {
        String bestNode = null;
        double minLatency = Double.MAX_VALUE;

        for (String node : availableNodes) {
            double ingressLatency = nodeService.getIngressLatency(area, node);
            if (ingressLatency < minLatency) {
                minLatency = ingressLatency;
                bestNode = node;
            }
        }
        return bestNode;
    }
    
}
