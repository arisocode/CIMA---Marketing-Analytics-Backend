package com.cimaxis.demo.marketing.controller.workflows;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutionResponse;
import com.cimaxis.demo.marketing.service.workflows.WorkflowExecutionService;

/**
 * Disparo y consulta de ejecuciones de workflows.
 */
@RestController
@RequestMapping("/api/v1/marketing/executions")
@RequiredArgsConstructor
public class WorkflowExecutionController {

    private final WorkflowExecutionService executionService;

    /** Ejecutar workflow sobre todos los clientes del CRM. */
    @PostMapping("/run/{workflowId}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<List<WorkflowExecutionResponse>> runWorkflow(
            @PathVariable Integer workflowId,
            HttpServletRequest request) {

        String token = extractToken(request);
        String userId = (String) request.getAttribute("userId");

        return ResponseEntity.ok(executionService.executeWorkflow(workflowId, token, userId));
    }

    /** Ejecutar workflow sobre un cliente especifico. */
    @PostMapping("/run/{workflowId}/client/{clientId}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<WorkflowExecutionResponse> runWorkflowForClient(
            @PathVariable Integer workflowId,
            @PathVariable String clientId,
            HttpServletRequest request) {

        String token = extractToken(request);
        String userId = (String) request.getAttribute("userId");

        return ResponseEntity.ok(
                executionService.executeWorkflowForClient(workflowId, clientId, token, userId));
    }

    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<WorkflowExecutionResponse>> getByWorkflow(
            @PathVariable Integer workflowId) {
        return ResponseEntity.ok(executionService.getExecutionsByWorkflow(workflowId));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<WorkflowExecutionResponse>> getByClient(
            @PathVariable String clientId) {
        return ResponseEntity.ok(executionService.getExecutionsByClient(clientId));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Falta la cabecera Authorization con el token Bearer");
    }
}
