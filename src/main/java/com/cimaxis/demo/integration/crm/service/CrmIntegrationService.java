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
        Object valor = client.get("name");
        if (esTexto(valor)) return valor.toString().trim();

        String first = textoDe(client, "first_name", "firstName");
        String last  = textoDe(client, "last_name", "lastName");
        if (first != null || last != null) {
            String completo = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            if (!completo.isEmpty()) return completo;
        }

        String empresa = textoDe(client, "company_name", "companyName");
        if (empresa != null) return empresa;

        String correo = extractClientEmail(client);
        if (correo != null && !correo.isBlank()) return correo;

        return "Cliente";
    }

    private boolean esTexto(Object valor) {
        return valor instanceof String s && !s.isBlank();
    }

    private String textoDe(Map<String, Object> origen, String... claves) {
        for (String clave : claves) {
            Object valor = origen.get(clave);
            if (esTexto(valor)) return valor.toString().trim();
        }
        return null;
    }

    public String extractClientEmail(Map<String, Object> client) {
        if (client == null) return null;
        Object email = client.get("email");
        if (email == null) email = client.get("contact_info");
        if (email == null) email = client.get("contactInfo");
        if (email == null) email = client.get("correo");
        if (email instanceof String value && value.contains("@")) {
            return value.trim();
        }
        return email != null ? email.toString() : null;
    }
}