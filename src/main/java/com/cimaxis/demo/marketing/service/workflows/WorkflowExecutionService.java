package com.cimaxis.demo.marketing.service.workflows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.cimaxis.demo.marketing.domain.templates.MessageTemplate;
import com.cimaxis.demo.marketing.repository.templates.MessageTemplateRepository;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.EmailService;
import com.cimaxis.demo.marketing.service.notifications.NotificationDispatcher;
import com.cimaxis.demo.marketing.service.templates.MessageRenderer;

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
    private final MessageTemplateRepository templateRepository;

    @Value("${cimaxis.scheduler.retry-delay-minutes:60}")
    private long retryDelayMinutes;

    @Value("${cimaxis.scheduler.max-delivery-attempts:3}")
    private long maxDeliveryAttempts;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    public WorkflowExecutionService(
            WorkflowRepository workflowRepository,
            WorkflowExecutionRepository executionRepository,
            MarketingInteractionRepository interactionRepository,
            CrmIntegrationService crmIntegrationService,
            NotificationDispatcher notificationDispatcher,
            WorkflowExecutionMapper executionMapper,
            ClientRepository clientRepository,
            MessageTemplateRepository templateRepository) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.interactionRepository = interactionRepository;
        this.crmIntegrationService = crmIntegrationService;
        this.notificationDispatcher = notificationDispatcher;
        this.executionMapper = executionMapper;
        this.clientRepository = clientRepository;
        this.templateRepository = templateRepository;
    }

    private <T> T executeInTransaction(TransactionCallback<T> action) {
        if (transactionManager != null) {
            if (transactionTemplate == null) {
                transactionTemplate = new TransactionTemplate(transactionManager);
            }
            return transactionTemplate.execute(action);
        }
        return action.doInTransaction(null);
    }

    /** Ejecuta un workflow sobre todos los clientes del CRM (CU-05). */
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
            if (!canDispatch(workflowId, clientId)) continue;

            results.add(runSingle(workflow, clientId, client, loggedByUserId).execution());
        }
        return executionMapper.toResponseList(results);
    }

    /** Ejecuta un workflow sobre un cliente puntual. */
    public WorkflowExecutionResponse executeWorkflowForClient(Integer workflowId,
                                                              String clientId,
                                                              String bearerToken,
                                                              String loggedByUserId) {

        Workflow workflow = requireActiveWorkflow(workflowId);

        if (!canDispatch(workflowId, clientId)) {
            throw new IllegalStateException(
                    "El workflow no puede ejecutarse todavia para el cliente " + clientId);
        }

        return executionMapper.toResponse(
                runSingle(workflow, clientId, resolveClientData(clientId, bearerToken), loggedByUserId).execution());
    }

    /**
     * Ejecuta un workflow sobre una lista concreta de clientes.
     */
    public List<WorkflowExecution> executeForClients(Workflow workflow,
                                                     List<String> clientIds,
                                                     String bearerToken,
                                                     String loggedByUserId) {
        List<WorkflowExecution> results = new ArrayList<>();
        for (String clientId : clientIds) {
            if (clientId == null) continue;
            if (!canDispatch(workflow.getWorkflowId(), clientId)) continue;
            results.add(runSingle(workflow, clientId, resolveClientData(clientId, bearerToken), loggedByUserId).execution());
        }
        return results;
    }

    /** Ejecucion guardada y resultado de la accion (canal, estado de entrega y detalle). */
    public record RunOutcome(WorkflowExecution execution, DispatchResult dispatch) {
    }

    /**
     * Ejecuta la accion para un cliente que ya paso las reglas de
     * WorkflowEligibilityService (planificador y "ejecutar ahora"); aqui no se
     * vuelven a evaluar.
     */
    public RunOutcome runFor(Workflow workflow, String clientId, String bearerToken, String loggedByUserId) {
        return runSingle(workflow, clientId, resolveClientData(clientId, bearerToken), loggedByUserId);
    }

    public List<WorkflowExecutionResponse> getExecutionsByWorkflow(Integer workflowId) {
        return executionMapper.toResponseList(executionRepository.findByWorkflowId(workflowId));
    }

    public List<WorkflowExecutionResponse> getExecutionsByClient(String clientId) {
        return executionMapper.toResponseList(executionRepository.findByClientId(clientId));
    }

    /**
     * Evita duplicar entregas exitosas y habilita reintentos acotados para
     * fallos transitorios. Cada reintento debe respetar la ventana configurada.
     */
    private boolean canDispatch(Integer workflowId, String clientId) {
        if (executionRepository.existsByWorkflowIdAndClientIdAndResult(
                workflowId, clientId, WorkflowExecution.ExecutionResult.success)) {
            return false;
        }

        long failedAttempts = executionRepository.countByWorkflowIdAndClientIdAndResult(
                workflowId, clientId, WorkflowExecution.ExecutionResult.failed);
        if (failedAttempts >= maxDeliveryAttempts) {
            return false;
        }

        return executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(
                        workflowId, clientId)
                .map(lastExecution -> {
                    // pending = omitido por el limite diario de correos: se reintenta al dia siguiente.
                    if (lastExecution.getResult() == WorkflowExecution.ExecutionResult.pending
                            && lastExecution.getExecutedAt() != null
                            && esHoyEnBogota(lastExecution.getExecutedAt())) {
                        return false;
                    }
                    return lastExecution.getExecutedAt() == null
                            || !lastExecution.getExecutedAt()
                                    .plusMinutes(retryDelayMinutes)
                                    .isAfter(LocalDateTime.now());
                })
                .orElse(true);
    }

    /** executedAt se guarda en la zona del servidor; el limite diario se corta en Bogota. */
    private static boolean esHoyEnBogota(LocalDateTime instanteLocal) {
        LocalDate fecha = instanteLocal.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(EmailService.ZONA_CIMA)
                .toLocalDate();
        return fecha.equals(LocalDate.now(EmailService.ZONA_CIMA));
    }

    private RunOutcome runSingle(Workflow workflow,
                                        String clientId,
                                        Map<String, Object> clientData,
                                        String loggedByUserId) {

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflow.getWorkflowId());
        execution.setClientId(clientId);
        execution.setExecutedAt(LocalDateTime.now());
        execution.setResult(WorkflowExecution.ExecutionResult.pending);

        Mensaje mensaje = prepararMensaje(workflow, clientId, clientData);
        execution.setSentMessage(mensaje.cuerpo());

        DispatchResult dispatch;
        try {
            dispatch = notificationDispatcher.dispatch(workflow, clientData, mensaje.asunto(), mensaje.cuerpo());
        } catch (Exception e) {
            log.error("Fallo el envio del workflow {} al cliente {}",
                    workflow.getWorkflowId(), clientId, e);
            dispatch = DispatchResult.failed("unknown", "Error al entregar la notificacion");
        }
        if (dispatch == null) {
            dispatch = DispatchResult.failed("unknown", "La accion del workflow no devolvio resultado");
        }

        if (dispatch.skippedByLimit()) {
            // No es un fallo: no consume intentos y se reintenta al dia siguiente.
            execution.setResult(WorkflowExecution.ExecutionResult.pending);
            execution.setErrorDetail(dispatch.detail());
        } else {
            execution.setResult(dispatch.delivered()
                    ? WorkflowExecution.ExecutionResult.success
                    : WorkflowExecution.ExecutionResult.failed);
            if (!dispatch.delivered()) {
                execution.setErrorDetail(dispatch.detail());
            }
        }

        final DispatchResult finalDispatch = dispatch;
        final WorkflowExecution toPersist = execution;
        WorkflowExecution guardada = executeInTransaction(status -> {
            WorkflowExecution saved = executionRepository.save(toPersist);
            registrarInteraccion(workflow, clientId, saved.getExecutionId(),
                    loggedByUserId, finalDispatch);
            return saved;
        });
        return new RunOutcome(guardada, finalDispatch);
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
        if (client.getAdditionalInfo() != null && !client.getAdditionalInfo().isBlank()) {
            map.put("name", client.getAdditionalInfo());
        }
        if (client.getPlan() != null) {
            map.put("plan", client.getPlan());
        }
        return map;
    }

    /** Asunto (para correo) y cuerpo ya con las variables reemplazadas. */
    record Mensaje(String asunto, String cuerpo) {
    }

    /**
     * Construye el mensaje desde la plantilla del workflow (si tiene una activa)
     * o desde su messageTemplate. Variables: {nombre}, {plan}, {empresa} y
     * {workflow}.
     */
    Mensaje prepararMensaje(Workflow workflow, String clientId, Map<String, Object> clientData) {
        Map<String, Object> datos = clientData != null ? clientData : Map.of();

        String nombre = crmIntegrationService.extractClientName(datos);
        String plan = datos.get("plan") != null ? datos.get("plan").toString() : null;
        if (plan == null && clientId != null) {
            plan = clientRepository.findById(clientId).map(Client::getPlan).orElse(null);
        }
        Object empresa = datos.get("company_name") != null ? datos.get("company_name") : datos.get("companyName");

        Map<String, String> variables = new HashMap<>(MessageRenderer.variables(
                nombre, plan, empresa != null ? empresa.toString() : null));
        variables.put(MessageRenderer.WORKFLOW, workflow.getWorkflowName());

        MessageTemplate plantilla = plantillaActiva(workflow);
        String cuerpo = plantilla != null ? plantilla.getBody() : workflow.getMessageTemplate();
        String asunto = plantilla != null && plantilla.getSubject() != null && !plantilla.getSubject().isBlank()
                ? plantilla.getSubject()
                : workflow.getWorkflowName();
        if (cuerpo == null || cuerpo.isBlank()) {
            cuerpo = "Seguimiento automático - " + workflow.getWorkflowName();
        }
        return new Mensaje(MessageRenderer.render(asunto, variables), MessageRenderer.render(cuerpo, variables));
    }

    /** Plantilla del workflow si existe y esta activa; si no, se usa messageTemplate. */
    private MessageTemplate plantillaActiva(Workflow workflow) {
        if (workflow.getTemplateId() == null) {
            return null;
        }
        MessageTemplate plantilla = templateRepository.findById(workflow.getTemplateId()).orElse(null);
        if (plantilla == null || !Boolean.TRUE.equals(plantilla.getActive())) {
            log.warn("La plantilla {} del workflow {} no existe o esta inactiva; se usa el mensaje del workflow",
                    workflow.getTemplateId(), workflow.getWorkflowId());
            return null;
        }
        return plantilla;
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
        interaction.setDeliveryStatus(dispatch.deliveryStatus() != null ? dispatch.deliveryStatus().name() : null);
        interaction.setDeliveryError(dispatch.delivered() ? null : dispatch.detail());
        return interactionRepository.save(interaction);
    }
}
