package it.unibo.cas.eventmanagement.services;

import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

@Service
public class KubernetesOrchestrationService {
    @Autowired
    private KubernetesClient client;

    public void deployAnalysisForArea(String area_name) throws IOException {
        // Read the template
        String template = new String(
                Objects.requireNonNull(getClass().getResourceAsStream("/templates/analysis-template.yaml"))
                        .readAllBytes());

        // Sanitize the area name for Kubernetes: only lowercase and
        // alphanumeric characters
        String sanitizedAreaId = getSanitizedId(area_name);

        String finalYaml = template.replace("[K8S_AREA_ID]", sanitizedAreaId)
                .replace("[AREA_ID]", area_name);

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

}
