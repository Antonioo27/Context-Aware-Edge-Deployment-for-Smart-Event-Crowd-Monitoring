package it.unibo.cas.eventanalysis.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class that provides the Kubernetes API client for the analysis service.
 *
 * It configures and exposes a singleton KubernetesClient} bean using the Fabric8 library.
 */
@Configuration
public class KubernetesConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}