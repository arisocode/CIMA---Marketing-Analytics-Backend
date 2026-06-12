package com.cimaxis.demo.marketing.controller.workflows;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.service.workflows.WorkflowExecutionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/marketing/executions")
public class WorkflowExecutionController {

    private final WorkflowExecutionService executionService;

    public WorkflowExecutionController(WorkflowExecutionService executionService) {
        this.executionService = executionService;
    }

    // Ejecutar workflow sobre todos los clientes del CRM
    @PostMapping("/run/{workflowId}")
    public ResponseEntity<List<WorkflowExecution>> runWorkflow(
            @PathVariable Integer workflowId,
            HttpServletRequest request) {

        String token = extractToken(request);
        String userId = (String) request.getAttribute("userId");

        List<WorkflowExecution> results =
                executionService.executeWorkflow(workflowId, token, userId);

        return ResponseEntity.ok(results);
    }

    // Ejecutar workflow sobre un cliente específico
    @PostMapping("/run/{workflowId}/client/{clientId}")
    public ResponseEntity<WorkflowExecution> runWorkflowForClient(
            @PathVariable Integer workflowId,
            @PathVariable String clientId,
            HttpServletRequest request) {

        String token = extractToken(request);
        String userId = (String) request.getAttribute("userId");

        WorkflowExecution result =
                executionService.executeWorkflowForClient(workflowId, clientId, token, userId);

        return ResponseEntity.ok(result);
    }

    // Consultar ejecuciones de un workflow
    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<WorkflowExecution>> getByWorkflow(@PathVariable Integer workflowId) {
        return ResponseEntity.ok(executionService.getExecutionsByWorkflow(workflowId));
    }

    // Consultar ejecuciones de un cliente
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<WorkflowExecution>> getByClient(@PathVariable String clientId) {
        return ResponseEntity.ok(executionService.getExecutionsByClient(clientId));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        throw new RuntimeException("Token no encontrado en la petición");
    }
}
