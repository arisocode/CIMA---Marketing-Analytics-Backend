package com.cimaxis.demo.marketing.service.workflows;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowRequest;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowResponse;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.templates.MessageTemplateService;

/**
 * Logica de negocio de workflows.
 */
@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final CampaignRepository campaignRepository;
    private final WorkflowMapper workflowMapper;
    private final MessageTemplateService templateService;

    public WorkflowService(WorkflowRepository workflowRepository,
                           CampaignRepository campaignRepository,
                           WorkflowMapper workflowMapper,
                           MessageTemplateService templateService) {
        this.workflowRepository = workflowRepository;
        this.campaignRepository = campaignRepository;
        this.workflowMapper = workflowMapper;
        this.templateService = templateService;
    }

    public List<WorkflowResponse> findAll() {
        return workflowMapper.toResponseList(workflowRepository.findAll());
    }

    public WorkflowResponse findById(Integer id) {
        return workflowMapper.toResponse(requireWorkflow(id));
    }

    public List<WorkflowResponse> findByCampaign(Integer campaignId) {
        return workflowMapper.toResponseList(workflowRepository.findByCampaignId(campaignId));
    }

    public List<WorkflowResponse> findActive() {
        return workflowMapper.toResponseList(workflowRepository.findByActiveTrue());
    }

    @Transactional
    public WorkflowResponse create(WorkflowRequest request) {
        if (request.getCampaignId() == null
                || !campaignRepository.existsById(request.getCampaignId())) {
            throw new IllegalArgumentException(
                    "No es posible crear el workflow: la campana indicada no existe");
        }

        validarPlantilla(request);
        Workflow workflow = workflowMapper.toEntity(request);
        workflow.setCreatedAt(LocalDateTime.now());
        if (workflow.getActive() == null) {
            workflow.setActive(true);
        }
        return workflowMapper.toResponse(workflowRepository.save(workflow));
    }

    @Transactional
    public WorkflowResponse update(Integer id, WorkflowRequest request) {
        Workflow existing = requireWorkflow(id);
        validarPlantilla(request);
        workflowMapper.aplicarCambios(request, existing);
        return workflowMapper.toResponse(workflowRepository.save(existing));
    }

    /**
     * Invierte el estado activo del workflow.
     */
    @Transactional
    public WorkflowResponse toggle(Integer id) {
        Workflow existing = requireWorkflow(id);
        existing.setActive(!Boolean.TRUE.equals(existing.getActive()));
        return workflowMapper.toResponse(workflowRepository.save(existing));
    }

    @Transactional
    public void delete(Integer id) {
        if (!workflowRepository.existsById(id)) {
            throw ResourceNotFoundException.de("Workflow", id);
        }
        workflowRepository.deleteById(id);
    }

    /** Si el workflow referencia una plantilla, debe existir y estar activa. */
    private void validarPlantilla(WorkflowRequest request) {
        if (request != null && request.getTemplateId() != null) {
            templateService.requireActive(request.getTemplateId());
        }
    }

    private Workflow requireWorkflow(Integer id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Workflow", id));
    }
}
