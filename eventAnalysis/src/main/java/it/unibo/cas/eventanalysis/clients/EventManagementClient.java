package it.unibo.cas.eventanalysis.clients;

import it.unibo.cas.eventanalysis.models.DTOs.AnalysisStatsDTO;
import it.unibo.cas.eventanalysis.models.entities.Alert;
import it.unibo.cas.eventanalysis.models.entities.AnalysisStats;
import it.unibo.cas.eventanalysis.models.entities.Area;
import it.unibo.cas.eventanalysis.config.AnalysisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EventManagementClient {
    @Value("${eventmanagement.api.url:http://event-management-svc:8080}")
    private String eventManagementApiUrl;
    private final RestTemplate restTemplate;

    @Autowired
    private AnalysisProperties properties;

    public EventManagementClient() {
        this.restTemplate = new RestTemplate();
    }

    public int getAreaCapacity(String area_id) {
        String url = eventManagementApiUrl + "/api/event/area/" + area_id + "/capacity";

        try {
            ResponseEntity<Integer> response = restTemplate.getForEntity(url, Integer.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException(
                        "Error occurs while try retrive information: " + response.getStatusCode());
            }
            return response.getBody();

        } catch (Exception e) {
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(),
                    e);
        }
    }

    public double getM2(String area_id) {
        String url = eventManagementApiUrl + "/api/event/area/" + area_id + "/m2";
        try {
            ResponseEntity<Double> response = restTemplate.getForEntity(url, Double.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException(
                        "Error occurs while try retrive information: " + response.getStatusCode());
            }
            return response.getBody();

        } catch (Exception e) {
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(),
                    e);
        }
    }

    public void sendAnalysis(AnalysisStatsDTO analysisStats) {
        // todo: complete the rest api requests
        String url = eventManagementApiUrl + "/api/event/area/analysis";
        try {
            restTemplate.postForEntity(url, analysisStats, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(),
                    e);
        }
    }

    public void sendAlert(Alert alert) {
        // todo: complete the rest api requests
        String url = eventManagementApiUrl + "/api/event/area/alert";
        try {
            restTemplate.postForEntity(url, alert, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(),
                    e);
        }
    }
}