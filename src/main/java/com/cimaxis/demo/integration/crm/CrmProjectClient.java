package com.cimaxis.demo.integration.crm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CrmProjectClient {

    private final String crmBaseUrl;
    private final RestTemplate restTemplate;

    public CrmProjectClient(
            @Value("${crm.base.url}") String crmBaseUrl,
            RestTemplate restTemplate
    ) {
        this.crmBaseUrl = crmBaseUrl;
        this.restTemplate = restTemplate;
    }

    public List<Map<String, Object>> getProjects(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        List<Map<String, Object>> projects = new ArrayList<>();
        int page = 1;
        int totalPages;
        do {
            String url = crmBaseUrl + "/api/v1/collab/projects?page=" + page + "&limit=100";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();
            totalPages = extractPageProjects(body, projects, page);
            page++;
        } while (page <= totalPages);
        return projects;
    }

    private int extractPageProjects(
            Map<String, Object> body,
            List<Map<String, Object>> projects,
            int currentPage
    ) {
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new IllegalStateException("Contrato inválido de Collab: data debe ser un objeto paginado");
        }
        Object items = dataMap.get("items");
        if (items instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> raw) {
                    projects.add(raw.entrySet().stream().collect(Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()), Map.Entry::getValue)));
                }
            }
        }
        Object totalPagesValue = dataMap.get("total_pages");
        return totalPagesValue instanceof Number number ? number.intValue() : currentPage;
    }
}
