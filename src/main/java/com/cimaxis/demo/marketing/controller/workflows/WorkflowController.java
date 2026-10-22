package com.cimaxis.demo.marketing.controller.workflows;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.workflows.WorkflowRequest;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowResponse;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowPreviewResponse;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowRunNowResponse;
import com.cimaxis.demo.marketing.service.workflows.WorkflowAutomationService;
import com.cimaxis.demo.marketing.service.workflows.WorkflowService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Endpoints de definicion de workflows.
 */
@RestController
@RequestMapping("/api/v1/marketing/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowAutomationService automationService;

    /** A quien le llegaria hoy la automatizacion y por que no a los demas (mismas reglas del planificador). */
    @GetMapping("/{id}/preview")
    public ResponseEntity<WorkflowPreviewResponse> preview(@PathVariable Integer id) {
        return ResponseEntity.ok(automationService.preview(id));
    }

    /** Evalua solo este workflow con las reglas del planificador y ejecuta la accion para los elegibles. */
    @PostMapping("/{id}/run-now")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<WorkflowRunNowResponse> runNow(@PathVariable Integer id, HttpServletRequest request) {
        // Igual que el planificador: usa los datos de clientes sincronizados en marketing (sin llamar al CRM).
        return ResponseEntity.ok(automationService.runNow(id, null, (String) request.getAttribute("userId")));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> getAll() {
        return ResponseEntity.ok(workflowService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(workflowService.findById(id));
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<WorkflowResponse>> getByCampaign(@PathVariable Integer campaignId) {
        return ResponseEntity.ok(workflowService.findByCampaign(campaignId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<WorkflowResponse>> getActive() {
        return ResponseEntity.ok(workflowService.findActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<WorkflowResponse> create(@RequestBody WorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<WorkflowResponse> update(@PathVariable Integer id,
                                                   @RequestBody WorkflowRequest request) {
        return ResponseEntity.ok(workflowService.update(id, request));
    }

    /** Activa o desactiva el workflow. */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<WorkflowResponse> toggle(@PathVariable Integer id) {
        return ResponseEntity.ok(workflowService.toggle(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        workflowService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
