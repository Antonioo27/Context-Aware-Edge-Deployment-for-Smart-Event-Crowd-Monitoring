package it.unibo.cas.eventanalysis.config;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.models.entities.Area;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


/**
 * Spring configuration class for the Event Analysis microservice.
 *
 * This class sets up core beans when the service starts:
 * - Enables configuration property binding using AnalysisProperties.
 * - Fetches area metadata (maximum capacity and surface area in square meters) from
 *   the central Event Management service to initialize the current Area bean.
 * - Provides a configured JSON ObjectMapper bean that handles Java 8 date/time types.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AnalysisProperties.class)
public class AnalysisConfiguration {

    /**
     * It creates the object Area in memory (Singleton) other class can use this object by calling
     * "@Autowired private Area area"
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

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); 
        return mapper;
    }

}
