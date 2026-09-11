package it.unibo.cas.eventmanagement.orchestration;

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;

/**
 * Service that interacts directly with the Kubernetes cluster API using Fabric8.
 *
 * Responsibilities:
 * - Creates, deletes, and migrates event-analysis worker pods by dynamically modifying deployment specs.
 * - Queries cluster health, node schedulability (cordoned status), and real-time CPU usage.
 * - Manages chaos experiments by scheduling CPU stress pods and toggling node cordoning.
 */
@Service
public class KubernetesOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesOrchestrationService.class);

    @Autowired
    private KubernetesClient client;

    /**
     * Deploys the analysis service deployment for a specific area using a YAML template.
     *
     * @param area_name name of the event area to monitor
     * @param targetNodeId identifier of the node where the pod should run (defaults to node-cloud)
     * @throws IOException if the template file cannot be read
     */
    public void deployAnalysisForArea(String area_name, String targetNodeId) throws IOException {
        String template = new String(
                Objects.requireNonNull(getClass().getResourceAsStream("/templates/analysis-template.yaml"))
                        .readAllBytes());

        String sanitizedAreaId = getSanitizedId(area_name);
        String nodeToDeploy = (targetNodeId != null && !targetNodeId.isBlank()) ? targetNodeId : "node-cloud";

        String finalYaml = template.replace("[K8S_AREA_ID]", sanitizedAreaId)
                .replace("[AREA_ID]", area_name)
                .replace("[TARGET_NODE_ID]", nodeToDeploy);

        InputStream inputStream = new ByteArrayInputStream(finalYaml.getBytes());
        client.load(inputStream)
                .forceConflicts()
                .serverSideApply();
    }

    /**
     * Removes all event-analysis deployments from the current namespace.
     *
     * @return true if deployments were successfully removed, false otherwise
     */
    public Boolean removeAllDeployAnalysis() {
        try {
            String currentNamespace = client.getNamespace();
            String deploymentName = "event-analysis";

            List<StatusDetails> result = client.apps().deployments()
                    .inNamespace(currentNamespace)
                    .withLabel("app", deploymentName)
                    .delete();

            return result != null && !result.isEmpty();
        } catch (KubernetesClientException e) {
            throw new RuntimeException("Error during the deletion of the deployment: " + e.getMessage(), e);
        }
    }
    
    public Boolean removeAnalysisForArea(String area_name) {
        try {
            String currentNamespace = client.getNamespace();
            String deploymentName = "event-analysis-" + getSanitizedId(area_name);

            List<StatusDetails> result = client.apps().deployments()
                    .inNamespace(currentNamespace)
                    .withName(deploymentName)
                    .delete();

            return result != null && !result.isEmpty();
        } catch (KubernetesClientException e) {
            throw new RuntimeException("Error during the deletion of the deployment: " + e.getMessage(), e);
        }
    }

    private String getSanitizedId(String area_name) {
        return area_name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    /**
     * Returns a map showing which nodes are currently healthy and schedulable.
     * A node is considered operational only if condition Ready is True and unschedulable is not set.
     *
     * @return map of node identifiers and their operational readiness status
     */
    public Map<String, Boolean> getNodeStatusMap() {
        Map<String, Boolean> statusMap = new HashMap<>();

        try {
            List<Node> nodes = client.nodes().list().getItems();
            for (Node node : nodes) {
                String nodeId = node.getMetadata().getLabels().get("node-id");
                if (nodeId == null) {
                    nodeId = node.getMetadata().getName();
                }

                boolean isReady = node.getStatus().getConditions().stream()
                        .anyMatch(cond -> "Ready".equals(cond.getType()) && "True".equals(cond.getStatus()));

                boolean isUnschedulable = node.getSpec() != null &&
                        Boolean.TRUE.equals(node.getSpec().getUnschedulable());

                boolean isHealthyAndSchedulable = isReady && !isUnschedulable;

                statusMap.put(nodeId, isHealthyAndSchedulable);
            }
        } catch (KubernetesClientException e) {
            System.err.println(" [ORCHESTRATOR] Errore nel recupero dello stato dei nodi: " + e.getMessage());
        }
        return statusMap;
    }

    /**
     * Reads the node-id label from the deployment nodeSelector to determine where an area pod is scheduled.
     *
     * @param areaName name of the area to inspect
     * @return node identifier hosting the pod, or node-cloud as fallback
     */
    public String getCurrentNodeForArea(String areaName) {
        String sanitizedAreaId = getSanitizedId(areaName);
        String deploymentName = "event-analysis-" + sanitizedAreaId;

        try {
            var deployment = client.apps().deployments()
                    .inNamespace(client.getNamespace())
                    .withName(deploymentName)
                    .get();

            if (deployment != null && deployment.getSpec() != null) {
                Map<String, String> nodeSelector = deployment.getSpec()
                        .getTemplate()
                        .getSpec()
                        .getNodeSelector();

                if (nodeSelector != null && nodeSelector.containsKey("node-id")) {
                    return nodeSelector.get("node-id");
                }
            }
        } catch (Exception e) {
            logger.error(" [ORCHESTRATOR] Errore durante il recupero del nodo corrente per l'area '{}': {}", areaName, e.getMessage());
        }
        return "node-cloud"; 
    }

    /**
     * Migrates an analysis pod to a different compute node by patching the nodeSelector in its deployment.
     *
     * @param areaName name of the area to migrate
     * @param targetNodeId identifier of the destination node
     * @return true if the patch succeeded, false otherwise
     */
    public boolean migratePodToNode(String areaName, String targetNodeId) {
        String sanitizedAreaId = getSanitizedId(areaName);
        String deploymentName = "event-analysis-" + sanitizedAreaId;
        String namespace = client.getNamespace();

        try {
            String patchJson = String.format(
                    "{\"spec\":{\"template\":{\"spec\":{\"nodeSelector\":{\"node-id\":\"%s\"}}}}}",
                    targetNodeId);

            client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .patch(patchJson);

            logger.info(" [ORCHESTRATOR] Migrazione applicata con successo su K8s per Area '{}' -> Node: {}", areaName,
                    targetNodeId);
            return true;
        } catch (Exception e) {
            logger.error(" [ORCHESTRATOR] Errore durante la migrazione dell'area '{}' su nodo '{}': {}", areaName,
                    targetNodeId, e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the current CPU utilization percentage for every node from the Kubernetes Metrics Server.
     *
     * @return map of node identifiers and their calculated CPU usage percentages
     */
    public Map<String, Double> getNodeCpuUsagePercentageMap() {
        Map<String, Double> cpuMap = new HashMap<>();
        try {
            var nodeMetricsList = client.top().nodes().metrics();
            for (var metric : nodeMetricsList.getItems()) {
                String nodeName = metric.getMetadata().getName();

                var k8sNode = client.nodes().withName(nodeName).get();
                String nodeId = (k8sNode != null && k8sNode.getMetadata().getLabels().containsKey("node-id"))
                        ? k8sNode.getMetadata().getLabels().get("node-id")
                        : nodeName;

                var usageQuantity = metric.getUsage().get("cpu");
                double usageMillicores = usageQuantity != null
                        ? usageQuantity.getNumericalAmount().doubleValue() * 1000.0
                        : 0.0;

                double allocatableMillicores = 2000.0;
                if (k8sNode != null && k8sNode.getStatus().getAllocatable().containsKey("cpu")) {
                    allocatableMillicores = k8sNode.getStatus().getAllocatable().get("cpu").getNumericalAmount()
                            .doubleValue() * 1000.0;
                }

                double cpuPercent = (usageMillicores / allocatableMillicores) * 100.0;
                cpuMap.put(nodeId, Math.min(100.0, Math.round(cpuPercent * 10.0) / 10.0));
            }
        } catch (Exception e) {
            logger.warn(" [METRICS] Metrics server non ancora pronto o non raggiungibile: {}", e.getMessage());
        }
        return cpuMap;
    }

    /**
     * Inspects all active analysis deployments to build a mapping from area names to current host nodes.
     *
     * @return map linking each area name to the node identifier running its analysis pod.
     */
    public Map<String, String> getCurrentAssignments() {
        Map<String, String> assignments = new HashMap<>();
        try {
            var deployments = client.apps().deployments()
                    .inNamespace(client.getNamespace())
                    .list().getItems();

            for (var deployment : deployments) {
                String name = deployment.getMetadata().getName();
                if (name.startsWith("event-analysis-")) {
                    String areaName = name.substring("event-analysis-".length());
                    String nodeId = "node-cloud";

                    if (deployment.getSpec() != null
                            && deployment.getSpec().getTemplate() != null
                            && deployment.getSpec().getTemplate().getSpec() != null) {
                        var nodeSelector = deployment.getSpec().getTemplate().getSpec().getNodeSelector();
                        if (nodeSelector != null && nodeSelector.containsKey("node-id")) {
                            nodeId = nodeSelector.get("node-id");
                        }
                    }
                    assignments.put(areaName, nodeId);
                }
            }
        } catch (Exception e) {
            logger.warn(" [METRICS] Errore nel recupero delle assegnazioni correnti: {}", e.getMessage());
        }
        return assignments;
    }

    /**
     * Counts how many analysis worker pods are currently running on each node.
     *
     * @return map with node identifiers and the count of assigned analysis pods
     */
    public Map<String, Integer> getNodePodCountMap() {
        Map<String, Integer> podCountMap = new HashMap<>();
        podCountMap.put("node-cloud", 0);
        podCountMap.put("node-edge-1", 0);
        podCountMap.put("node-edge-2", 0);
        podCountMap.put("node-edge-3", 0);

        Map<String, String> assignments = getCurrentAssignments();
        for (String nodeId : assignments.values()) {
            podCountMap.put(nodeId, podCountMap.getOrDefault(nodeId, 0) + 1);
        }

        return podCountMap;
    }

    /**
     * Launches a stress-ng pod on a target node to simulate high CPU utilization for a fixed duration.
     *
     * @param nodeId target node identifier where the stress workload should run
     * @param durationSeconds duration of the stress test in seconds
     * @return true if the stress pod was created successfully, false otherwise
     */
    public boolean startNodeCpuStress(String nodeId, int durationSeconds) {
        String podName = "cpu-stress-" + nodeId;
        String namespace = "default";

        try {
            client.pods().inNamespace(namespace).withName(podName).delete();

            Pod stressPod = new PodBuilder()
                    .withNewMetadata()
                        .withName(podName)
                        .withNamespace(namespace)
                        .addToLabels("app", "cpu-stress")
                        .addToLabels("node-id", nodeId)
                    .endMetadata()
                    .withNewSpec()
                        .withRestartPolicy("Never")
                        .addToNodeSelector("node-id", nodeId)
                        .addNewContainer()
                            .withName("stressor")
                            .withImage("polinux/stress-ng")
                            .withArgs("--cpu", "0", "--cpu-load", "95", "--timeout", durationSeconds + "s")
                        .endContainer()
                    .endSpec()
                    .build();

            client.pods().inNamespace(namespace).resource(stressPod).create();
            logger.info("[STRESS-NG] Pod {} avviato con successo su nodo '{}' (durata: {}s)", podName, nodeId, durationSeconds);
            return true;
        } catch (Exception e) {
            logger.error("Errore durante l'avvio del Pod di stress sul nodo {}: {}", nodeId, e.getMessage());
            return false;
        }
    }

    /**
     * Terminates and removes an active stress-ng pod from the cluster.
     *
     * @param nodeId identifier of the node running the stress test
     * @return true if the stress pod was deleted, false otherwise
     */
    public boolean stopNodeCpuStress(String nodeId) {
        String podName = "cpu-stress-" + nodeId;
        try {
            var deleted = client.pods().inNamespace("default").withName(podName).delete();
            logger.info("[STRESS-NG] Pod {} terminato manualmente", podName);
            return deleted != null && !deleted.isEmpty();
        } catch (Exception e) {
            logger.error("Errore durante l'eliminazione del Pod di stress per nodo {}: {}", nodeId, e.getMessage());
            return false;
        }
    }

    /**
     * Sets or removes the unschedulable flag on a Kubernetes node (cordon/uncordon).
     *
     * @param nodeId identifier of the node to update
     * @param cordoned true to make the node unschedulable, false to restore scheduling
     * @return true if the node patch succeeded, false otherwise
     */
    public boolean setNodeCordon(String nodeId, boolean cordoned) {
        try {
            List<Node> k8sNodes = client.nodes().list().getItems();
            for (Node node : k8sNodes) {
                String labelNodeId = (node.getMetadata() != null && node.getMetadata().getLabels() != null)
                        ? node.getMetadata().getLabels().get("node-id")
                        : null;
                String k8sName = node.getMetadata() != null ? node.getMetadata().getName() : null;

                if (nodeId.equals(labelNodeId) || nodeId.equals(k8sName)) {
                    String patchJson = String.format("{\"spec\":{\"unschedulable\":%b}}", cordoned);

                    client.nodes()
                            .withName(k8sName)
                            .patch(patchJson);

                    logger.info("[NODE CORDON] Nodo '{}' (K8s: '{}') impostato a unschedulable={}", nodeId, k8sName, cordoned);
                    return true;
                }
            }
            logger.warn("[NODE CORDON] Nodo '{}' non trovato nel cluster K8s", nodeId);
            return false;
        } catch (Exception e) {
            logger.error("Errore durante cordon/uncordon del nodo {}: {}", nodeId, e.getMessage());
            return false;
        }
    }

    /**
     * Collects all node identifiers that are running active stress-ng pods[cite: 1].
     *
     * @return list of node identifiers currently under CPU stress[cite: 1]
     */
    public List<String> getStressedNodeIds() {
        List<String> stressed = new ArrayList<>();
        try {
            List<Pod> pods = client.pods().inNamespace("default").withLabel("app", "cpu-stress").list().getItems();
            for (Pod p : pods) {
                String phase = p.getStatus() != null ? p.getStatus().getPhase() : "";
                if ("Running".equalsIgnoreCase(phase) || "Pending".equalsIgnoreCase(phase)) {
                    String nodeId = p.getMetadata().getLabels() != null ? p.getMetadata().getLabels().get("node-id") : null;
                    String nodeName = p.getSpec() != null ? p.getSpec().getNodeName() : null;
                    if (nodeId != null) stressed.add(nodeId);
                    if (nodeName != null && !stressed.contains(nodeName)) stressed.add(nodeName);
                }
            }
        } catch (Exception e) {
            logger.error("Errore nel recupero dei pod di stress attivi: {}", e.getMessage());
        }
        return stressed;
    }

    /**
     * Collects all node identifiers that are currently marked as unschedulable (cordoned).
     *
     * @return list of cordoned node identifiers
     */
    public List<String> getCordonedNodeIds() {
        List<String> cordoned = new ArrayList<>();
        try {
            List<Node> k8sNodes = client.nodes().list().getItems();
            for (Node n : k8sNodes) {
                if (n.getSpec() != null && Boolean.TRUE.equals(n.getSpec().getUnschedulable())) {
                    String labelNodeId = (n.getMetadata() != null && n.getMetadata().getLabels() != null)
                            ? n.getMetadata().getLabels().get("node-id")
                            : null;
                    String k8sName = n.getMetadata() != null ? n.getMetadata().getName() : null;

                    if (labelNodeId != null) {
                        cordoned.add(labelNodeId);
                    }
                    if (k8sName != null && !cordoned.contains(k8sName)) {
                        cordoned.add(k8sName);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Errore nel recupero dei nodi cordonati: {}", e.getMessage());
        }
        return cordoned;
    }

}
