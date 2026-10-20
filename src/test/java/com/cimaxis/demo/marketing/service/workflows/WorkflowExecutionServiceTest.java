package com.cimaxis.demo.marketing.service.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.templates.MessageTemplate;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowExecutionMapper;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.templates.MessageTemplateRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.NotificationDispatcher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowExecutionServiceTest {

    private static final Integer WORKFLOW_ID = 10;
    private static final String CLIENT_ID = "client-1";

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowExecutionRepository executionRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private CrmIntegrationService crmIntegrationService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private WorkflowExecutionMapper executionMapper;
    @Mock private ClientRepository clientRepository;
    @Mock private MessageTemplateRepository templateRepository;

    @InjectMocks private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "retryDelayMinutes", 60L);
        ReflectionTestUtils.setField(service, "maxDeliveryAttempts", 3L);
        when(executionRepository.existsByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.success)).thenReturn(false);
        when(executionRepository.save(any(WorkflowExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(interactionRepository.save(any(MarketingInteraction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void notDispatchWhenAPreviousDeliverySucceeded() {
        when(executionRepository.existsByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.success)).thenReturn(true);

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    void notDispatchWhenFailureAttemptsReachTheConfiguredLimit() {
        when(executionRepository.countByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.failed)).thenReturn(3L);

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    void notDispatchAgainBeforeTheRetryWindowExpires() {
        when(executionRepository.countByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.failed)).thenReturn(1L);
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(
                WORKFLOW_ID, CLIENT_ID)).thenReturn(Optional.of(executionAt(LocalDateTime.now(), WorkflowExecution.ExecutionResult.failed)));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    void retryFailedDeliveryAfterTheConfiguredWindow() {
        when(executionRepository.countByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.failed)).thenReturn(1L);
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(
                WORKFLOW_ID, CLIENT_ID)).thenReturn(Optional.of(executionAt(LocalDateTime.now().minusMinutes(61), WorkflowExecution.ExecutionResult.failed)));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());
        when(notificationDispatcher.dispatch(any(), any(), any(), any())).thenReturn(DispatchResult.ok("email", "Enviado"));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).hasSize(1);

        verify(notificationDispatcher).dispatch(any(), any(), any(), any());
    }

    @Test
    void storesTheDeliveryStatusOfTheEmailInTheInteraction() {
        when(notificationDispatcher.dispatch(any(), any(), any(), any()))
                .thenReturn(DispatchResult.queued("email", "Encolado en crm-media"));

        List<WorkflowExecution> resultado = service.executeForClients(workflow(), List.of(CLIENT_ID), null, null);

        assertThat(resultado.get(0).getResult()).isEqualTo(WorkflowExecution.ExecutionResult.success);
        MarketingInteraction interaccion = interaccionGuardada();
        assertThat(interaccion.getDeliveryStatus()).isEqualTo("QUEUED");
        assertThat(interaccion.getDeliveryError()).isNull();
        assertThat(interaccion.getInteractionType()).isEqualTo(MarketingInteraction.InteractionType.message);
    }

    @Test
    void anEmailSkippedByTheDailyLimitIsPendingNotFailed() {
        when(notificationDispatcher.dispatch(any(), any(), any(), any()))
                .thenReturn(DispatchResult.skippedByLimit("email", "No se envió: se alcanzó el límite diario"));

        List<WorkflowExecution> resultado = service.executeForClients(workflow(), List.of(CLIENT_ID), null, null);

        assertThat(resultado.get(0).getResult()).isEqualTo(WorkflowExecution.ExecutionResult.pending);
        assertThat(resultado.get(0).getErrorDetail()).contains("límite diario");
        MarketingInteraction interaccion = interaccionGuardada();
        assertThat(interaccion.getDeliveryStatus()).isEqualTo("SKIPPED_LIMIT");
        assertThat(interaccion.getDeliveryError()).contains("límite diario");
    }

    @Test
    void anExecutionSkippedTodayByTheLimitIsRetriedTomorrowNotThisHour() {
        // Sin ventana de reintento, solo la regla del limite diario puede bloquear el envio.
        ReflectionTestUtils.setField(service, "retryDelayMinutes", 0L);
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(WORKFLOW_ID, CLIENT_ID))
                .thenReturn(Optional.of(executionAt(LocalDateTime.now().minusSeconds(1), WorkflowExecution.ExecutionResult.pending)));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    void aFailedExecutionIsRetriedWhenTheWindowAllowsIt() {
        ReflectionTestUtils.setField(service, "retryDelayMinutes", 0L);
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(WORKFLOW_ID, CLIENT_ID))
                .thenReturn(Optional.of(executionAt(LocalDateTime.now().minusSeconds(1), WorkflowExecution.ExecutionResult.failed)));
        when(notificationDispatcher.dispatch(any(), any(), any(), any())).thenReturn(DispatchResult.queued("email", "Encolado"));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).hasSize(1);
    }

    @Test
    void anExecutionSkippedOnAPreviousDayIsRetried() {
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(WORKFLOW_ID, CLIENT_ID))
                .thenReturn(Optional.of(executionAt(LocalDateTime.now().minusDays(2), WorkflowExecution.ExecutionResult.pending)));
        when(notificationDispatcher.dispatch(any(), any(), any(), any())).thenReturn(DispatchResult.queued("email", "Encolado"));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).hasSize(1);
    }

    @Test
    void buildsSubjectAndBodyFromTheActiveTemplateWithClientVariables() {
        UUID plantillaId = UUID.randomUUID();
        Workflow workflow = workflow();
        workflow.setTemplateId(plantillaId);
        workflow.setMessageTemplate("Mensaje viejo del workflow");
        when(templateRepository.findById(plantillaId)).thenReturn(Optional.of(plantilla(plantillaId, true)));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(
                Client.builder().clientId(CLIENT_ID).additionalInfo("Ana").contactInfo("ana@test.com").plan("Oro").build()));
        when(crmIntegrationService.extractClientName(anyMap())).thenReturn("Ana");
        when(notificationDispatcher.dispatch(any(), any(), any(), any())).thenReturn(DispatchResult.queued("email", "Encolado"));

        List<WorkflowExecution> resultado = service.executeForClients(workflow, List.of(CLIENT_ID), null, null);

        verify(notificationDispatcher).dispatch(eq(workflow), anyMap(),
                eq("Hola Ana, novedades de tu plan"), eq("Tu plan Oro con Seguimiento"));
        assertThat(resultado.get(0).getSentMessage()).isEqualTo("Tu plan Oro con Seguimiento");
    }

    @Test
    void anInactiveTemplateFallsBackToTheWorkflowMessage() {
        UUID plantillaId = UUID.randomUUID();
        Workflow workflow = workflow();
        workflow.setTemplateId(plantillaId);
        workflow.setMessageTemplate("Hola {nombre}");
        when(templateRepository.findById(plantillaId)).thenReturn(Optional.of(plantilla(plantillaId, false)));
        when(crmIntegrationService.extractClientName(anyMap())).thenReturn("Ana");
        when(notificationDispatcher.dispatch(any(), any(), any(), any())).thenReturn(DispatchResult.queued("email", "Encolado"));

        service.executeForClients(workflow, List.of(CLIENT_ID), null, null);

        verify(notificationDispatcher).dispatch(eq(workflow), anyMap(), eq("Seguimiento"), eq("Hola Ana"));
    }

    private MarketingInteraction interaccionGuardada() {
        ArgumentCaptor<MarketingInteraction> captor = ArgumentCaptor.forClass(MarketingInteraction.class);
        verify(interactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private Workflow workflow() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(WORKFLOW_ID);
        workflow.setCampaignId(20);
        workflow.setWorkflowName("Seguimiento");
        workflow.setActionType(Workflow.ActionType.send_email);
        return workflow;
    }

    private static MessageTemplate plantilla(UUID id, boolean activa) {
        return MessageTemplate.builder().templateId(id).name("Novedades").channel(MessageTemplate.Channel.EMAIL)
                .subject("Hola {nombre}, novedades de tu plan").body("Tu plan {plan} con {workflow}")
                .active(activa).createdAt(LocalDateTime.now()).build();
    }

    private WorkflowExecution executionAt(LocalDateTime executedAt, WorkflowExecution.ExecutionResult result) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setExecutedAt(executedAt);
        execution.setResult(result);
        return execution;
    }
}
