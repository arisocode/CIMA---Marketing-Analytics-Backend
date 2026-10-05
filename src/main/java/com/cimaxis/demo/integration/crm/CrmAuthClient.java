package com.cimaxis.demo.integration.crm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class CrmAuthClient {

    private final String crmBaseUrl;
    private final RestTemplate restTemplate;

    public CrmAuthClient(
            @Value("${crm.base.url}") String crmBaseUrl,
            RestTemplate restTemplate
    ) {
        this.crmBaseUrl = crmBaseUrl;
        this.restTemplate = restTemplate;
    }

    public String login(String email, String password) {
        String url = crmBaseUrl + "/api/v1/auth/login";

        Map<String, String> body = Map.of("email", email, "password", password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        Map<?, ?> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("data")) {
            Map<?, ?> data = (Map<?, ?>) responseBody.get("data");
            if (data != null && data.containsKey("access_token")) {
                return (String) data.get("access_token");
            }
        }
        throw new RuntimeException("No se pudo obtener el token del CRM");
    }
}