package it.unibo.cas.eventanalysis.config;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.models.entities.Area;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Slf4j
@Configuration
@EnableConfigurationProperties(AnalysisProperties.class)
public class AnalysisConfiguration {

    /**
     * Define area Singleton bean.
     * This will be instantiated once on analysis application start.
     */
    @Bean
    public Area currentArea(EventManagementClient eventManagementClient, AnalysisProperties properties) {
        // call the backend to retrieve area capacity and m^2
        int capacity = eventManagementClient.getAreaCapacity(properties.areaId());
        double m2 = eventManagementClient.getM2(properties.areaId());
        Area area = new Area(properties.areaId(), capacity, m2);
        log.info("EventAnalysisApplication initialization - area: [{}]", area.id());
        return area;
    }

    /**
     * Define ObjectMapper bean to be injected in ProbeSubscriber
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Support for OffsetDateTime
        return mapper;
    }

}
