package com.cimaxis.demo.marketing.controller.segmentation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.segmentation.ClientSegmentationService;
import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;
import com.cimaxis.demo.marketing.service.workflows.WorkflowExecutionService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Segmentacion de clientes y ejecucion dirigida de workflows.
 */
@RestController
@RequestMapping("/api/v1/marketing/segments")
@RequiredArgsConstructor
public class SegmentController {

    private final ClientSegmentationService segmentationService;
    private final WorkflowExecutionService executionService;
    private final WorkflowRepository workflowRepository;

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody SegmentCriteria criteria) {
        List<Client> clientes = segmentationService.segment(criteria);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("total", clientes.size());
        respuesta.put("clients", clientes);
        return ResponseEntity.ok(respuesta);
    }

    /** Ejecuta un workflow unicamente sobre los clientes del segmento. */
    @PostMapping("/{workflowId}/execute")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<List<WorkflowExecution>> executeOnSegment(
            @PathVariable Integer workflowId,
            @RequestBody SegmentCriteria criteria,
            HttpServletRequest request) {

        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> ResourceNotFoundException.de("Workflow", workflowId));

        String userId = (String) request.getAttribute("userId");
        List<String> objetivo = segmentationService.segmentIds(criteria);

        return ResponseEntity.ok(
                executionService.executeForClients(workflow, objetivo, extractToken(request), userId));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
