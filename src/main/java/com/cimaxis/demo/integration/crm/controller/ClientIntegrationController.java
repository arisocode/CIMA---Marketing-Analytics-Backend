package com.cimaxis.demo.integration.crm.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.integration.crm.service.ClientOverviewService;
import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.integration.crm.service.CrmSyncService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Exposicion de la informacion de clientes y proyectos que el modulo consume
 * del CRM base, y disparo manual de la sincronizacion.
 */
@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
public class ClientIntegrationController {

    private final CrmIntegrationService crmIntegrationService;
    private final ClientOverviewService clientOverviewService;
    private final CrmSyncService crmSyncService;

    /** Clientes registrados en el CRM, consultados en vivo. */
    @GetMapping("/clients")
    public ResponseEntity<List<Map<String, Object>>> getClients(HttpServletRequest request) {
        return ResponseEntity.ok(crmIntegrationService.getClients(extractToken(request)));
    }

    /** Datos basicos de un cliente puntual. */
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<Map<String, Object>> getClient(@PathVariable String clientId,
                                                         HttpServletRequest request) {
        return crmIntegrationService.getClients(extractToken(request)).stream()
                .filter(c -> clientId.equals(crmIntegrationService.extractClientId(c)))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + clientId));
    }

    /** Proyectos registrados en el CRM. */
    @GetMapping("/projects")
    public ResponseEntity<List<Map<String, Object>>> getProjects(HttpServletRequest request) {
        return ResponseEntity.ok(crmIntegrationService.getProjects(extractToken(request)));
    }

    /** Proyectos vinculados a un cliente, desde la base compartida. */
    @GetMapping("/clients/{clientId}/projects")
    public ResponseEntity<List<Project>> getClientProjects(@PathVariable String clientId) {
        return ResponseEntity.ok(clientOverviewService.getProjectsByClient(clientId));
    }

    /** Ficha consolidada del cliente. */
    @GetMapping("/clients/{clientId}/overview")
    public ResponseEntity<Map<String, Object>> getClientOverview(@PathVariable String clientId,
                                                                 HttpServletRequest request) {
        return ResponseEntity.ok(
                clientOverviewService.getOverview(clientId, extractToken(request)));
    }

    /**
     * Fuerza la sincronizacion de clientes y proyectos del CRM hacia el esquema
     * de marketing.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Map<String, Object>> sync(HttpServletRequest request) {
        return ResponseEntity.ok(crmSyncService.syncAll(extractToken(request)));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
