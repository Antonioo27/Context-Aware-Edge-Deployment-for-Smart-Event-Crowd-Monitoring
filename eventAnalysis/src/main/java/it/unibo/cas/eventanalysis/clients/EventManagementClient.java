package it.unibo.cas.eventanalysis.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
            throw new RuntimeException("Error occurs while try to comunicate with EventManagement: " + e.getMessage(),
                    e);
        }
    }

    public double getM2(String area_id) {
        String url = eventManagementApiUrl + "/api/event/area/" + area_id + "/m2";
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
}