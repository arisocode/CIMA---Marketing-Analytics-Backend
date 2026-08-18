package com.cimaxis.demo.marketing.service.workflows;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutionResponse;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowExecutionMapper;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.NotificationDispatcher;

/**
 * Motor de ejecucion de workflows de marketing.
 */
@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final CrmIntegrationService crmIntegrationService;
    private final NotificationDispatcher notificationDispatcher;
    private final WorkflowExecutionMapper executionMapper;
    private final ClientRepository clientRepository;

    public WorkflowExecutionService(
            WorkflowRepository workflowRepository,
            WorkflowExecutionRepository executionRepository,
            MarketingInteractionRepository interactionRepository,
            CrmIntegrationService crmIntegrationService,
            NotificationDispatcher notificationDispatcher,
            WorkflowExecutionMapper executionMapper,
            ClientRepository clientRepository) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.interactionRepository = interactionRepository;
        this.crmIntegrationService = crmIntegrationService;
        this.notificationDispatcher = notificationDispatcher;
        this.executionMapper = executionMapper;
        this.clientRepository = clientRepository;
    }

    /** Ejecuta un workflow sobre todos los clientes del CRM (CU-05). */
    @Transactional
    public List<WorkflowExecutionResponse> executeWorkflow(Integer workflowId,
                                                           String bearerToken,
                                                           String loggedByUserId) {

        Workflow workflow = requireActiveWorkflow(workflowId);
        List<Map<String, Object>> clients = crmIntegrationService.getClients(bearerToken);

        if (clients.isEmpty()) {
            log.warn("El workflow {} no se ejecuto: la campana no tiene clientes asociados", workflowId);
            return List.of();
        }

        List<WorkflowExecution> results = new ArrayList<>();
        for (Map<String, Object> client : clients) {
            String clientId = crmIntegrationService.extractClientId(client);
            if (clientId == null) continue;
            if (executionRepository.existsByWorkflowIdAndClientId(workflowId, clientId)) continue;

            results.add(runSingle(workflow, clientId, client, loggedByUserId));
        }
        return executionMapper.toResponseList(results);
    }

    /** Ejecuta un workflow sobre un cliente puntual. */
    @Transactional
    public WorkflowExecutionResponse executeWorkflowForClient(Integer workflowId,
                                                              String clientId,
                                                              String bearerToken,
                                                              String loggedByUserId) {

        Workflow workflow = requireActiveWorkflow(workflowId);

        if (executionRepository.existsByWorkflowIdAndClientId(workflowId, clientId)) {
            throw new IllegalStateException(
                    "Este workflow ya fue ejecutado para el cliente " + clientId);
        }

        return executionMapper.toResponse(
                runSingle(workflow, clientId, resolveClientData(clientId, bearerToken), loggedByUserId));
    }

    /**
     * Ejecuta un workflow sobre una lista concreta de clientes.
     */
    @Transactional
    public List<WorkflowExecution> executeForClients(Workflow workflow,
                                                     List<String> clientIds,
                                                     String bearerToken,
                                                     String loggedByUserId) {
        List<WorkflowExecution> results = new ArrayList<>();
        for (String clientId : clientIds) {
            if (clientId == null) continue;
            if (executionRepository.existsByWorkflowIdAndClientId(workflow.getWorkflowId(), clientId)) continue;
            results.add(runSingle(workflow, clientId, resolveClientData(clientId, bearerToken), loggedByUserId));
        }
        return results;
    }

    public List<WorkflowExecutionResponse> getExecutionsByWorkflow(Integer workflowId) {
        return executionMapper.toResponseList(executionRepository.findByWorkflowId(workflowId));
    }

    public List<WorkflowExecutionResponse> getExecutionsByClient(String clientId) {
        return executionMapper.toResponseList(executionRepository.findByClientId(clientId));
    }

    private WorkflowExecution runSingle(Workflow workflow,
                                        String clientId,
                                        Map<String, Object> clientData,
                                        String loggedByUserId) {

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflow.getWorkflowId());
        execution.setClientId(clientId);
        execution.setExecutedAt(LocalDateTime.now());
        execution.setResult(WorkflowExecution.ExecutionResult.pending);

        String message = buildMessage(workflow, clientData);
        execution.setSentMessage(message);

        DispatchResult dispatch;
        try {
            dispatch = notificationDispatcher.dispatch(workflow, clientData, message);
        } catch (Exception e) {
            log.error("Fallo el envio del workflow {} al cliente {}",
                    workflow.getWorkflowId(), clientId, e);
            dispatch = DispatchResult.failed("unknown", "Error al entregar la notificacion");
        }

        execution.setResult(dispatch.delivered()
                ? WorkflowExecution.ExecutionResult.success
                : WorkflowExecution.ExecutionResult.failed);
        if (!dispatch.delivered()) {
            execution.setErrorDetail(dispatch.detail());
        }

        // Se persiste primero la ejecucion para disponer del execution_id
        execution = executionRepository.save(execution);

        // La trazabilidad se registra incluso si la entrega fallo: el intento
        // de contacto tambien es informacion comercial.
        registrarInteraccion(workflow, clientId, execution.getExecutionId(),
                loggedByUserId, dispatch);

        return execution;
    }

    private Workflow requireActiveWorkflow(Integer workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> ResourceNotFoundException.de("Workflow", workflowId));
        if (Boolean.FALSE.equals(workflow.getActive())) {
            throw new IllegalStateException("El workflow esta inactivo");
        }
        return workflow;
    }

    /* private Map<String, Object> resolveClientData(String clientId, String bearerToken) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", clientId);
        if (bearerToken == null) return fallback;
        try {
            return crmIntegrationService.getClients(bearerToken).stream()
                    .filter(c -> clientId.equals(crmIntegrationService.extractClientId(c)))
                    .findFirst()
                    .orElse(fallback);
        } catch (Exception e) {
            log.warn("No se pudo consultar el cliente {} en el CRM: {}", clientId, e.getMessage());
            return fallback;
        }
    } */

    private Map<String, Object> resolveClientData(String clientId, String bearerToken) {
        if (bearerToken == null) {
            return clientRepository.findById(clientId)
                    .map(this::toClientMap)
                    .orElseGet(() -> Map.of("id", clientId));
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", clientId);
        try {
            return crmIntegrationService.getClients(bearerToken).stream()
                    .filter(c -> clientId.equals(crmIntegrationService.extractClientId(c)))
                    .findFirst()
                    .orElse(fallback);
        } catch (Exception e) {
            log.warn("No se pudo consultar el cliente {} en el CRM: {}", clientId, e.getMessage());
            return fallback;
        }
    }

    private Map<String, Object> toClientMap(Client client) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", client.getClientId());
        map.put("email", client.getContactInfo());
        return map;
    }

    private String buildMessage(Workflow workflow, Map<String, Object> client) {
        String template = workflow.getMessageTemplate();
        if (template == null || template.isBlank()) {
            return "Seguimiento automatico - " + workflow.getWorkflowName();
        }
        String nombre = crmIntegrationService.extractClientName(client);
        return template.replace("{nombre}", nombre)
                .replace("{workflow}", workflow.getWorkflowName());
    }

    private MarketingInteraction registrarInteraccion(Workflow workflow,
                                                      String clientId,
                                                      Integer executionId,
                                                      String loggedBy,
                                                      DispatchResult dispatch) {
        MarketingInteraction interaction = new MarketingInteraction();
        interaction.setCampaignId(workflow.getCampaignId());
        interaction.setClientId(clientId);
        interaction.setExecutionId(executionId);
        interaction.setLoggedBy(loggedBy);
        interaction.setContactDate(LocalDateTime.now());
        interaction.setChannel(dispatch.channel());
        interaction.setInteractionType(dispatch.delivered()
                ? MarketingInteraction.InteractionType.message
                : MarketingInteraction.InteractionType.no_response);
        return interactionRepository.save(interaction);
    }
}
