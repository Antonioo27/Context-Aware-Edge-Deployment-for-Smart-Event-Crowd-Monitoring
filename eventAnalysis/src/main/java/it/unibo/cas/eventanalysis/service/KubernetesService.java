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
   
        String podName = System.getenv("HOSTNAME");

        if (podName != null && !podName.isEmpty()) {
            Pod pod = kubernetesClient.pods().withName(podName).get();
            if (pod != null && pod.getSpec() != null) {
                return pod.getSpec().getNodeName();
            }
        }

        String envNodeName = System.getenv("NODE_NAME");
        if (envNodeName != null && !envNodeName.isEmpty()) {
            return envNodeName;
        }

        return "Unknown";
    }
}
