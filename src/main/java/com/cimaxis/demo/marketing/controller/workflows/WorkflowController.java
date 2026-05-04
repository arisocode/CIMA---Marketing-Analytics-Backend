package com.cimaxis.demo.marketing.controller.workflows;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/marketing/workflows")
public class WorkflowController {

    private final WorkflowRepository workflowRepository;

    public WorkflowController(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    // Obtener todos los workflows
    @GetMapping
    public ResponseEntity<List<Workflow>> getAll() {
        return ResponseEntity.ok(workflowRepository.findAll());
    }

    // Obtener workflow por ID
    @GetMapping("/{id}")
    public ResponseEntity<Workflow> getById(@PathVariable Integer id) {
        return workflowRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Obtener workflows por campaña
    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<Workflow>> getByCampaign(@PathVariable Integer campaignId) {
        return ResponseEntity.ok(workflowRepository.findByCampaignId(campaignId));
    }

    // Obtener workflows activos
    @GetMapping("/active")
    public ResponseEntity<List<Workflow>> getActive() {
        return ResponseEntity.ok(workflowRepository.findByActiveTrue());
    }

    // Crear workflow
    @PostMapping
    public ResponseEntity<Workflow> create(@RequestBody Workflow workflow) {
        workflow.setCreatedAt(LocalDateTime.now());
        if (workflow.getActive() == null) workflow.setActive(true);
        return ResponseEntity.ok(workflowRepository.save(workflow));
    }

    // Actualizar workflow
    @PutMapping("/{id}")
    public ResponseEntity<Workflow> update(@PathVariable Integer id,
                                            @RequestBody Workflow updated) {
        return workflowRepository.findById(id).map(existing -> {
            existing.setWorkflowName(updated.getWorkflowName());
            existing.setDescription(updated.getDescription());
            existing.setTriggerType(updated.getTriggerType());
            existing.setNoContactDays(updated.getNoContactDays());
            existing.setActionType(updated.getActionType());
            existing.setMessageTemplate(updated.getMessageTemplate());
            existing.setActive(updated.getActive());
            return ResponseEntity.ok(workflowRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Activar o desactivar workflow
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Workflow> toggle(@PathVariable Integer id) {
        return workflowRepository.findById(id).map(existing -> {
            existing.setActive(!existing.getActive());
            return ResponseEntity.ok(workflowRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Eliminar workflow
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!workflowRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        workflowRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
