package com.cimaxis.demo.marketing.controller.segmentation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentActiveRequest;
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentRequest;
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentResponse;
import com.cimaxis.demo.marketing.dto.segments.SegmentClientResponse;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.segments.SavedSegmentService;
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
    private final SavedSegmentService savedSegmentService;

    // ------------------------------------------------------------------ segmentos guardados

    @GetMapping("/saved")
    public ResponseEntity<List<SavedSegmentResponse>> listSaved() {
        return ResponseEntity.ok(savedSegmentService.list());
    }

    @PostMapping("/saved")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<SavedSegmentResponse> createSaved(@RequestBody SavedSegmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedSegmentService.create(request));
    }

    @PutMapping("/saved/{segmentId}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<SavedSegmentResponse> updateSaved(@PathVariable UUID segmentId,
                                                            @RequestBody SavedSegmentRequest request) {
        return ResponseEntity.ok(savedSegmentService.update(segmentId, request));
    }

    @PatchMapping("/saved/{segmentId}/active")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<SavedSegmentResponse> setSavedActive(@PathVariable UUID segmentId,
                                                               @RequestBody SavedSegmentActiveRequest request) {
        return ResponseEntity.ok(savedSegmentService.setActive(segmentId, request != null ? request.active() : null));
    }

    @GetMapping("/saved/{segmentId}/clients")
    public ResponseEntity<List<SegmentClientResponse>> savedClients(@PathVariable UUID segmentId) {
        return ResponseEntity.ok(savedSegmentService.clients(segmentId));
    }

    // ------------------------------------------------------------------ vista previa y ejecucion

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
