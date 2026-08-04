package it.unibo.cas.eventanalysis.config;

import it.unibo.cas.eventanalysis.clients.EventManagementClient;
import it.unibo.cas.eventanalysis.models.entities.Area;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AreaConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(AreaConfiguration.class);

    @Autowired
    private EventManagementClient eventManagementClient;

    /**
     * Read area ID from environment variables.
     */
    @Value("${AREA_ID:default-area}")
    private String areaId;



    /**
     * Define area Singleton bean.
     * This will be instantiated once on analysis application start.
     */
    @Bean
    public Area currentArea() {
        // call the backend to retrieve area capacity
        int capacity = eventManagementClient.getAreaCapacity(areaId);
        Area area = new Area(areaId, capacity);
        logger.info("EventAnalysisApplication initialization - area: [{}]", area.id());
        return area;
    }
}
