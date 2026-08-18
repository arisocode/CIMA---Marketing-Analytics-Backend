package com.cimaxis.demo.integration.crm.service;

import com.cimaxis.demo.integration.crm.CrmAuthClient;
import com.cimaxis.demo.integration.crm.CrmClientClient;
import com.cimaxis.demo.integration.crm.CrmProjectClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CrmIntegrationService {

    private final CrmAuthClient crmAuthClient;
    private final CrmClientClient crmClientClient;
    private final CrmProjectClient crmProjectClient;

    public CrmIntegrationService(CrmAuthClient crmAuthClient,
                                  CrmClientClient crmClientClient,
                                  CrmProjectClient crmProjectClient) {
        this.crmAuthClient = crmAuthClient;
        this.crmClientClient = crmClientClient;
        this.crmProjectClient = crmProjectClient;
    }

    public List<Map<String, Object>> getClients(String bearerToken) {
        try {
            return crmClientClient.getClients(bearerToken);
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener clientes del CRM: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getProjects(String bearerToken) {
        try {
            return crmProjectClient.getProjects(bearerToken);
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener proyectos del CRM: " + e.getMessage());
        }
    }

    public String extractClientId(Map<String, Object> client) {
        Object id = client.get("id");
        if (id == null) id = client.get("clientId");
        if (id == null) id = client.get("client_id");
        if (id == null) id = client.get("subject");
        if (id instanceof String) return (String) id;
        if (id != null) return id.toString();
        return null;
    }

    public String extractClientName(Map<String, Object> client) {
        if (client == null) return "Cliente";
        if (client.get("name") != null && !client.get("name").toString().isBlank()) {
            return client.get("name").toString();
        }
        Object first = client.get("first_name") != null ? client.get("first_name") : client.get("firstName");
        Object last = client.get("last_name") != null ? client.get("last_name") : client.get("lastName");
        if (first != null || last != null) {
            String fullName = ((first != null ? first.toString() : "") + " " + (last != null ? last.toString() : "")).trim();
            if (!fullName.isBlank()) return fullName;
        }
        if (client.get("company_name") != null && !client.get("company_name").toString().isBlank()) {
            return client.get("company_name").toString();
        }
        if (client.get("companyName") != null && !client.get("companyName").toString().isBlank()) {
            return client.get("companyName").toString();
        }
        if (client.get("email") != null && !client.get("email").toString().isBlank()) {
            return client.get("email").toString();
        }
        return "Cliente";
    }
}