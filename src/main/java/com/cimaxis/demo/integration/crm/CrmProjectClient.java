package com.cimaxis.demo.integration.crm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.List;
import java.util.Map;

@Component
public class CrmProjectClient {

    @Value("${crm.base.url}")
    private String crmBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> getProjects(String bearerToken) {
        String url = crmBaseUrl + "/api/v1/core/projects/";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, request, Map.class);

        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("projects")) {
            return (List<Map<String, Object>>) body.get("projects");
        }
        return List.of();
    }
}