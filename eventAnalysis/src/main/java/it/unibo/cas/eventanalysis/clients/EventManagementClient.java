package it.unibo.cas.eventanalysis.clients;

import it.unibo.cas.eventanalysis.models.DTOs.AnalysisStatsDTO;
import it.unibo.cas.eventanalysis.models.entities.Alert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * REST client component for synchronous HTTP communication between the local
 * event analysis service and the central Event Management backend.
 * 
 * This client is responsabile for:
 *  1. Fetching static contextual metada of the target monitored area (capacity, m²) during initialization.
 *  2. Forwarding periodic crowd analysis statistics to the persistence layer.
 *  3. Transmitting critical crowd condition alerts along the persistent slow-path.
 */

@Component
public class EventManagementClient {
    @Value("${eventmanagement.api.url:http://event-management-svc:8080}")
    private String eventManagementApiUrl;
    private final RestTemplate restTemplate;


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
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(), e);
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
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(), e);
        }
    }

    public void sendAnalysis(AnalysisStatsDTO analysisStats) {
        String url = eventManagementApiUrl + "/api/event/area/analysis";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, analysisStats, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "Error occurs while try retrive information: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(), e);
        }
    }

    public void sendAlert(Alert alert) {
        String url = eventManagementApiUrl + "/api/event/area/alert";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, alert, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "Error occurs while try retrive information: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(), e);
        }
    }
}
