package com.cimaxis.demo.marketing.mapper.workflows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowRequest;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowResponse;

@Component
public class WorkflowMapper {

    public WorkflowResponse toResponse(Workflow workflow) {
        if (workflow == null) {
            return null;
        }
        return WorkflowResponse.builder()
                .workflowId(workflow.getWorkflowId())
                .campaignId(workflow.getCampaignId())
                .workflowName(workflow.getWorkflowName())
                .description(workflow.getDescription())
                .triggerType(workflow.getTriggerType() != null
                        ? workflow.getTriggerType().name() : null)
                .noContactDays(workflow.getNoContactDays())
                .actionType(workflow.getActionType() != null
                        ? workflow.getActionType().name() : null)
                .messageTemplate(workflow.getMessageTemplate())
                .active(workflow.getActive())
                .createdAt(workflow.getCreatedAt())
                .build();
    }

    public List<WorkflowResponse> toResponseList(List<Workflow> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return Collections.emptyList();
        }
        return workflows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Workflow toEntity(WorkflowRequest request) {
        return Workflow.builder()
                .campaignId(request.getCampaignId())
                .workflowName(request.getWorkflowName())
                .description(request.getDescription())
                .triggerType(parseTrigger(request.getTriggerType()))
                .noContactDays(request.getNoContactDays())
                .actionType(parseAction(request.getActionType()))
                .messageTemplate(request.getMessageTemplate())
                .active(request.getActive())
                .build();
    }

    public void aplicarCambios(WorkflowRequest request, Workflow destino) {
        destino.setWorkflowName(request.getWorkflowName());
        destino.setDescription(request.getDescription());
        destino.setTriggerType(parseTrigger(request.getTriggerType()));
        destino.setNoContactDays(request.getNoContactDays());
        destino.setActionType(parseAction(request.getActionType()));
        destino.setMessageTemplate(request.getMessageTemplate());
        if (request.getActive() != null) {
            destino.setActive(request.getActive());
        }
    }

    public Workflow.TriggerType parseTrigger(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Workflow.TriggerType.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de disparador invalido: " + valor
                    + ". Valores permitidos: " + Arrays.toString(Workflow.TriggerType.values()));
        }
    }

    public Workflow.ActionType parseAction(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Workflow.ActionType.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de accion invalido: " + valor
                    + ". Valores permitidos: " + Arrays.toString(Workflow.ActionType.values()));
        }
    }
}
