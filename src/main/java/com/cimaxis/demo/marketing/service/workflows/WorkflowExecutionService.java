package com.cimaxis.demo.marketing.service.workflows;


import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowExecutionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final CrmIntegrationService crmIntegrationService;

    public WorkflowExecutionService(
            WorkflowRepository workflowRepository,
            WorkflowExecutionRepository executionRepository,
            MarketingInteractionRepository interactionRepository,
            CrmIntegrationService crmIntegrationService) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.interactionRepository = interactionRepository;
        this.crmIntegrationService = crmIntegrationService;
    }

    // Ejecuta un workflow sobre todos los clientes del CRM
    public List<WorkflowExecution> executeWorkflow(Integer workflowId,
                                                    String bearerToken,
                                                    Integer loggedByUserId) {

        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow no encontrado: " + workflowId));

        if (!workflow.getActive()) {
            throw new RuntimeException("El workflow está inactivo");
        }

        List<Map<String, Object>> clients = crmIntegrationService.getClients(bearerToken);

        List<WorkflowExecution> results = new ArrayList<>();

        for (Map<String, Object> client : clients) {
            Integer clientId = crmIntegrationService.extractClientId(client);
            if (clientId == null) continue;

            boolean yaEjecutado = executionRepository
                    .existsByWorkflowIdAndClientId(workflowId, clientId);
            if (yaEjecutado) continue;

            WorkflowExecution execution = new WorkflowExecution();
            execution.setWorkflowId(workflowId);
            execution.setClientId(clientId);
            execution.setExecutedAt(LocalDateTime.now());

            try {
                String mensaje = buildMessage(workflow, client);
                execution.setSentMessage(mensaje);
                execution.setResult(WorkflowExecution.ExecutionResult.success);

                registrarInteraccion(workflow, clientId, null, loggedByUserId);

            } catch (Exception e) {
                execution.setResult(WorkflowExecution.ExecutionResult.failed);
                execution.setErrorDetail(e.getMessage());
            }

            results.add(executionRepository.save(execution));
        }

        return results;
    }

    public WorkflowExecution executeWorkflowForClient(Integer workflowId,
                                                   Integer clientId,
                                                   String bearerToken,
                                                   Integer loggedByUserId) {

        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow no encontrado: " + workflowId));

        if (!workflow.getActive()) {
            throw new RuntimeException("El workflow está inactivo");
        }

        boolean yaEjecutado = executionRepository
                .existsByWorkflowIdAndClientId(workflowId, clientId);
        if (yaEjecutado) {
            throw new RuntimeException("Este workflow ya fue ejecutado para el cliente " + clientId);
        }

        Map<String, Object> clientData = new java.util.HashMap<>();
        try {
            List<Map<String, Object>> clients = crmIntegrationService.getClients(bearerToken);
            clientData = clients.stream()
                    .filter(c -> clientId.equals(crmIntegrationService.extractClientId(c)))
                    .findFirst()
                    .orElse(Map.of("clientId", clientId));
        } catch (Exception e) {
            clientData.put("clientId", clientId);
        }

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setClientId(clientId);
        execution.setExecutedAt(LocalDateTime.now());

        try {
            String mensaje = buildMessage(workflow, clientData);
            execution.setSentMessage(mensaje);
            execution.setResult(WorkflowExecution.ExecutionResult.success);

            execution = executionRepository.save(execution);

            MarketingInteraction interaction = registrarInteraccion(
                    workflow, clientId, execution.getExecutionId(), loggedByUserId);
            interactionRepository.save(interaction);

            return execution;

        } catch (Exception e) {
            execution.setResult(WorkflowExecution.ExecutionResult.failed);
            execution.setErrorDetail(e.getMessage());
            return executionRepository.save(execution);
        }
    }

    private String buildMessage(Workflow workflow, Map<String, Object> client) {
        String template = workflow.getMessageTemplate();
        if (template == null || template.isBlank()) {
            return "Seguimiento automático - " + workflow.getWorkflowName();
        }
        String nombre = crmIntegrationService.extractClientName(client);
        return template.replace("{nombre}", nombre)
                       .replace("{workflow}", workflow.getWorkflowName());
    }

    private MarketingInteraction registrarInteraccion(Workflow workflow,
                                                       Integer clientId,
                                                       Integer executionId,
                                                       Integer loggedBy) {
        MarketingInteraction interaction = new MarketingInteraction();
        interaction.setCampaignId(workflow.getCampaignId());
        interaction.setClientId(clientId);
        interaction.setExecutionId(executionId);
        interaction.setLoggedBy(loggedBy);
        interaction.setContactDate(LocalDateTime.now());
        interaction.setChannel(resolveChannel(workflow.getActionType()));
        interaction.setInteractionType(MarketingInteraction.InteractionType.message);
        return interactionRepository.save(interaction);
    }

    private String resolveChannel(Workflow.ActionType actionType) {
        return switch (actionType) {
            case send_email -> "email";
            case send_whatsapp -> "whatsapp";
            case log_followup -> "internal";
            case notify_admin -> "admin_notification";
        };
    }

    public List<WorkflowExecution> getExecutionsByWorkflow(Integer workflowId) {
        return executionRepository.findByWorkflowId(workflowId);
    }

    public List<WorkflowExecution> getExecutionsByClient(Integer clientId) {
        return executionRepository.findByClientId(clientId);
    }
}