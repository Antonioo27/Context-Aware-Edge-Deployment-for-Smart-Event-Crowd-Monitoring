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

@Service
public class KubernetesOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesOrchestrationService.class);

    @Autowired
    private KubernetesClient client;

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
        // Metodo moderno e raccomandato da Fabric8
        client.load(inputStream)
                .forceConflicts()
                .serverSideApply();
    }

    public Boolean removeAllDeployAnalysis() {
        try {
            String currentNamespace = client.getNamespace();
            String deploymentName = "event-analysis";

            // remove all deployment with label "event-analysis"
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

                // 2. Verifica se il nodo è stato cordonato (unschedulable = true)
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

        }
        return "node-cloud"; // Fallback
    }

    public boolean migratePodToNode(String areaName, String targetNodeId) {
        String sanitizedAreaId = getSanitizedId(areaName);
        String deploymentName = "event-analysis-" + sanitizedAreaId;
        String namespace = client.getNamespace();

        try {
            // Patch JSON: modify only the node-id in the nodeSelector
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
     * Return a nodeId -> usagePercentageCPU map
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
     * Return the { areaName -> currentNodeId } map 
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
     * How many analysis pods has every node
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
     * Start a stress-ng Pod on the targetNode by the nodeSelector.
     */
    public boolean startNodeCpuStress(String nodeId, int durationSeconds) {
        String podName = "cpu-stress-" + nodeId;
        String namespace = "default";

        try {
            // Elimina eventuale pod precedente con lo stesso nome
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
     * Interrupt the stress pod
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

    public boolean isNodeUnderStress(String nodeId) {
        String podName = "cpu-stress-" + nodeId;
        Pod pod = client.pods().inNamespace("default").withName(podName).get();
        return pod != null && "Running".equalsIgnoreCase(pod.getStatus().getPhase());
    }

    /**
     * Set the state of cordon or uncordon of a node 
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
                    // Patch mirata sul solo campo unschedulable di spec
                    String patchJson = String.format("{\"spec\":{\"unschedulable\":%b}}", cordoned);

                    client.nodes()
                            .withName(k8sName)
                            .patch(patchJson);

                    logger.info("🔌 [NODE CORDON] Nodo '{}' (K8s: '{}') impostato a unschedulable={}", nodeId, k8sName, cordoned);
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
     * Return the list of id for every node who has a stress pod
     * 
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


    public List<String> getCordonedNodeIds() {
        List<String> cordoned = new ArrayList<>();
        try {
            List<Node> k8sNodes = client.nodes().list().getItems();
            for (Node n : k8sNodes) {
                // Se il nodo non è mai stato cordonato, getUnschedulable() restituisce null
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
