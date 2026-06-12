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
        if (client.containsKey("name")) return (String) client.get("name");
        if (client.containsKey("first_name") || client.containsKey("firstName")) {
            String first = (String) (client.containsKey("first_name") ? client.get("first_name") : client.get("firstName"));
            String last = (String) (client.containsKey("last_name") ? client.get("last_name") : client.get("lastName"));
            if (first != null || last != null) {
                return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            }
        }
        if (client.containsKey("company_name")) return (String) client.get("company_name");
        if (client.containsKey("companyName")) return (String) client.get("companyName");
        if (client.containsKey("email")) return (String) client.get("email");
        return "Cliente desconocido";
    }
}