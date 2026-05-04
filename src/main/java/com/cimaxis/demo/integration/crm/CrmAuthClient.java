package com.cimaxis.demo.integration.crm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;

@Component
public class CrmAuthClient {

    @Value("${crm.base.url}")
    private String crmBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String login(String email, String password) {
        String url = crmBaseUrl + "/api/v1/core/users/login";

        Map<String, String> body = Map.of("email", email, "password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        Map<?, ?> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("accessToken")) {
            return (String) responseBody.get("accessToken");
        }
        throw new RuntimeException("No se pudo obtener el token del CRM");
    }
}