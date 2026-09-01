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

@Component
public class OrchestratorControlLoop {
    
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorControlLoop.class);

    // Soglia di isteresi
    private static final double ISTERESI_THRESHOLD = 15.0;

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

    @Scheduled(fixedRate = 10000)
    public void tick() {
        OrchestrationPolicy currentPolicy = orchestrationController.getCurrentPolicyInternal();
        List<Area> areas = areaRepository.findAll();

        if (areas.isEmpty()) {
            return;
        }

        logger.info(" [CONTROL LOOP] Tick avviato. Politica attiva: {}", currentPolicy);

        // Politica CLOUD_ONLY
        if (currentPolicy == OrchestrationPolicy.CLOUD_ONLY) {
            handleCloudOnlyPolicy(areas);
            return;
        }

        // Politica CONTEXT_AWARE
        if (currentPolicy == OrchestrationPolicy.CONTEXT_AWARE) {
            handleContextAwarePolicy(areas);
            return;
        }

        // Politica STATIC
        if (currentPolicy == OrchestrationPolicy.STATIC) {
            handleStaticPolicy(areas);
        }

    }

    // Politica Cloud-Only: sposta tutti i nodi su cloud
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

    // Politica Static
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
            
            // Trova il nodo naturale migliore per vicinanza geografica (L_ingresso)
            String targetStaticNode = findStaticBestNode(area, availableNodes);

            if (targetStaticNode == null) {
                targetStaticNode = "node-cloud";
            }

            // Se non è sul suo nodo statico ottimale (es. all'avvio o dopo un ripristino guasto), spostalo
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
     * Politica Context-Aware: valuta stato nodi, calcola matrice costi
     * gestisce guasti e sposta i Pod solo se superano la soglia di isteresi
     */
    private void handleContextAwarePolicy(List<Area> areas) {
        // OBSERVE
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

        // Mappa del carico attuale (quanti Pod girano su ciascun nodo)
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

        // Ordinamento per priorità statica crescente e poi per criticità dinamica crescente:
        // le aree meno critiche vengono valutate per prime per essere evacuate per prime
        List<Area> sortedAreas = new ArrayList<>(areas);
        sortedAreas.sort(Comparator.comparingInt((Area a) -> Priority.getRankOrDefault(a.getPriority()))
                                   .thenComparingInt(a -> State.getRankOrDefault(a.getState())));

        // Lettura metriche CPU reali dei nodi
        Map<String, Double> nodeCpuMap = kubernetesOrchestrationService.getNodeCpuUsagePercentageMap();

        // Se un nodo ha CPU > 75%, viene penalizzato fortemente per i nuovi pod
        // e i pod meno critici già presenti vengono spinti verso la migrazione
        double CPU_OVERLOAD_THRESHOLD = 75.0;

        // Insieme dei nodi che hanno già evacuato 1 Pod in questo tick
        Set<String> nodesShedInThisTick = new HashSet<>();

        // EVALUATE and DECIDE per ogni area
        for (Area area : sortedAreas) {
            String currentNode = currentAssignments.get(area.getName());   
            boolean currentNodeIsHealthy = Boolean.TRUE.equals(nodeHealthMap.get(currentNode));
            Double currentCpu = nodeCpuMap.getOrDefault(currentNode, 0.0);
            boolean isCurrentNodeOverloaded = currentCpu > CPU_OVERLOAD_THRESHOLD;
            
            // Stato attuale dell'area
            State currentState = area.getState() != null ? area.getState() : State.NONE;

            // Se un nodo ha già evacuato un Pod in questo cilo non applichiamo la penalità agli altri Pod
            boolean applyOverloadPenaltyOnCurrent = isCurrentNodeOverloaded && !nodesShedInThisTick.contains(currentNode);

            if (isCurrentNodeOverloaded && !nodesShedInThisTick.contains(currentNode)) {
                logger.warn(" [OVERLOAD] Il nodo '{}' ha un utilizzo CPU del {}% (Soglia: {}%)!",
                        currentNode, String.format("%.1f", currentCpu), CPU_OVERLOAD_THRESHOLD);
            }

            if (!currentNodeIsHealthy) {
                logger.warn(" [FAILOVER] Il nodo attuale '{}' per l'area '{}' è offline. Ricalcolo immediato!", currentNode, area.getName());
            }

            String bestNode = currentNode;
            double currentCost = Double.MAX_VALUE;

            // Se il nodo attuale è sano, calcoliamo il suo costo attuale
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

            // Cerchiamo tra tutti i nodi candidati sani
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

                // Regola di decisione con Isteresi (o Failover forzato se il nodo attuale è morto)
                if (!currentNodeIsHealthy || candidateCost < (currentCost - ISTERESI_THRESHOLD)) {
                    if (candidateCost < minCost) {
                        minCost = candidateCost;
                        bestNode = candidateNode;
                    }
                }
            }

            // ACT: Esegui la migrazione se il nodo migliore è diverso da quello attuale
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
                    // Aggiorna bilanciamento locale
                    if (nodePodCount.containsKey(currentNode)) {
                        nodePodCount.put(currentNode, nodePodCount.get(currentNode) - 1);
                    }
                    if (nodePodCount.containsKey(bestNode)) {
                        nodePodCount.put(bestNode, nodePodCount.get(bestNode) + 1);
                    }

                    // Se la migrazione è dovuta a sovraccarico, blocchiamo ulteriori spostamenti da questo nodo per questo tick
                    if (isCurrentNodeOverloaded) {
                        nodesShedInThisTick.add(currentNode);
                    }
                }
            }
        
        }
    }

    /**
     * Individua il nodo con la minore latenza di ingresso geografica (PostGIS)
     * senza considerare folla, alert, CPU o carichi di altri Pod.
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
