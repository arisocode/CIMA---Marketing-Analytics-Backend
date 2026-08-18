package com.cimaxis.demo.marketing.controller.segmentation;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.marketing.service.segmentation.ClientPlanService;

import lombok.RequiredArgsConstructor;

/**
 * Vista de marketing sobre los clientes.
 */
@RestController
@RequestMapping("/api/v1/marketing/clients")
@RequiredArgsConstructor
public class ClientPlanController {

    private final ClientPlanService clientPlanService;

    /** Clientes sincronizados, con su plan comercial. */
    @GetMapping
    public ResponseEntity<List<Client>> getAll() {
        return ResponseEntity.ok(clientPlanService.findAll());
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<Client> getById(@PathVariable String clientId) {
        return ResponseEntity.ok(clientPlanService.findById(clientId));
    }

    @GetMapping("/plan/{plan}")
    public ResponseEntity<List<Client>> getByPlan(@PathVariable String plan) {
        return ResponseEntity.ok(clientPlanService.findByPlan(plan));
    }

    /** Clientes pendientes de clasificar comercialmente. */
    @GetMapping("/sin-plan")
    public ResponseEntity<List<Client>> getSinPlan() {
        return ResponseEntity.ok(clientPlanService.findSinPlan());
    }

    /** Asigna el plan a un cliente. Cuerpo: {"plan": "Oro"} */
    @PatchMapping("/{clientId}/plan")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<Client> assignPlan(@PathVariable String clientId,
                                             @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(clientPlanService.assignPlan(clientId, body.get("plan")));
    }

    /** Asignacion masiva. Cuerpo: {"uuid-1": "Oro", "uuid-2": "Premium"} */
    @PatchMapping("/plans")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<Map<String, Object>> assignPlanBulk(
            @RequestBody Map<String, String> asignaciones) {
        return ResponseEntity.ok(clientPlanService.assignPlanBulk(asignaciones));
    }
}
