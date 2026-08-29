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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
            // Patch JSON mirata: modifica esclusivamente il node-id nel nodeSelector
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
     * Restituisce una mappa nodeId -> percentuale Utilizzo CPU
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
     * Recupera la mappa completa { areaName -> currentNodeId } interrogando tutti i Deployment di analisi attivi
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
     * Conta quanti Pod di analisi sono attualmente allocati su ciascun nodo
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

}
