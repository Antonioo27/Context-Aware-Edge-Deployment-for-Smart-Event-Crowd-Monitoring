package it.unibo.cas.eventmanagement.services;

import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
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
    @Autowired
    private KubernetesClient client;

    public void deployAnalysisForArea(String area_name, String targetNodeId) throws IOException {
        // Read the template
        String template = new String(
                Objects.requireNonNull(getClass().getResourceAsStream("/templates/analysis-template.yaml"))
                        .readAllBytes());

        // Sanitize the area name for Kubernetes: only lowercase and
        // alphanumeric characters
        String sanitizedAreaId = getSanitizedId(area_name);

        String nodeToDeploy = (targetNodeId != null && !targetNodeId.isBlank()) ? targetNodeId : "node-cloud";

        String finalYaml = template.replace("[K8S_AREA_ID]", sanitizedAreaId)
                .replace("[AREA_ID]", area_name)
                .replace("[TARGET_NODE_ID]", nodeToDeploy);

        InputStream inputStream = new ByteArrayInputStream(finalYaml.getBytes());
        client.load(inputStream).serverSideApply();
    }

    public Boolean removeAnalysisForArea(String area_name) {
        try {
            String currentNamespace = client.getNamespace();
            String deploymentName = "event-analysis-" + getSanitizedId(area_name);

            List<io.fabric8.kubernetes.api.model.StatusDetails> result = client.apps().deployments()
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

                statusMap.put(nodeId, isReady);
            }
        }
        catch (KubernetesClientException e) {

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
        return "node-cloud"; //Fallback
    }

    public boolean migratePodToNode(String areaName, String targetNodeId) {
        String sanitizedAreaId = getSanitizedId(areaName);
        String deploymentName = "event-analysis-" + sanitizedAreaId;
        String namespace = client.getNamespace();

        try {
            client.apps().deployments()
                        .inNamespace(namespace)
                        .withName(deploymentName)
                        .edit(d -> new DeploymentBuilder(d)
                                .editSpec()
                                    .editTemplate()
                                        .editSpec()
                                            .addToNodeSelector("node-id", targetNodeId)
                                        .endSpec()
                                    .endTemplate()
                                .endSpec()
                                .build());
                    
            System.out.println(" [ORCHESTRATOR] Migration initiated for Area '" + areaName + "' -> Node: " + targetNodeId);                    
            return true;        
        } catch (KubernetesClientException e) {
            System.err.println(" [ORCHESTRATOR] Migration failed for Area '" + areaName + "': " + e.getMessage());
            return false;
        }
    }

}
