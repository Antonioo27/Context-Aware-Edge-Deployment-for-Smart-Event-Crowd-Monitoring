package it.unibo.cas.eventanalysis.service;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KubernetesService {
    @Autowired
    private KubernetesClient kubernetesClient;

    public String getNodeName() {
        // In Kubernetes, the HOSTNAME environment variable typically contains the pod
        // name
        String podName = System.getenv("HOSTNAME");

        if (podName != null && !podName.isEmpty()) {
            // Retrieve the pod details using the Fabric8 Kubernetes client
            Pod pod = kubernetesClient.pods().withName(podName).get();
            if (pod != null && pod.getSpec() != null) {
                // Return the node name where this pod is scheduled
                return pod.getSpec().getNodeName();
            }
        }

        // Fallback: Check if NODE_NAME is directly injected via the Downward API
        String envNodeName = System.getenv("NODE_NAME");
        if (envNodeName != null && !envNodeName.isEmpty()) {
            return envNodeName;
        }

        return "Unknown";
    }
}
