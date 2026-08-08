package it.unibo.cas.eventanalysis.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}