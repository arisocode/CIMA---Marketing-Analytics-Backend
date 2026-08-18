package com.cimaxis.demo.marketing.service.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowExecutionMapper;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.NotificationDispatcher;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "retryDelayMinutes", 60L);
        ReflectionTestUtils.setField(service, "maxDeliveryAttempts", 3L);
        when(executionRepository.existsByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.success)).thenReturn(false);
    }

    @Test
    void notDispatchWhenAPreviousDeliverySucceeded() {
        when(executionRepository.existsByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.success)).thenReturn(true);

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void notDispatchWhenFailureAttemptsReachTheConfiguredLimit() {
        when(executionRepository.countByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.failed)).thenReturn(3L);

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void notDispatchAgainBeforeTheRetryWindowExpires() {
        when(executionRepository.countByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.failed)).thenReturn(1L);
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(
                WORKFLOW_ID, CLIENT_ID)).thenReturn(Optional.of(executionAt(LocalDateTime.now())));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).isEmpty();

        verify(notificationDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    void retryFailedDeliveryAfterTheConfiguredWindow() {
        when(executionRepository.countByWorkflowIdAndClientIdAndResult(
                WORKFLOW_ID, CLIENT_ID, WorkflowExecution.ExecutionResult.failed)).thenReturn(1L);
        when(executionRepository.findFirstByWorkflowIdAndClientIdOrderByExecutedAtDesc(
                WORKFLOW_ID, CLIENT_ID)).thenReturn(Optional.of(executionAt(LocalDateTime.now().minusMinutes(61))));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());
        when(notificationDispatcher.dispatch(any(), any(), any())).thenReturn(DispatchResult.ok("email", "Enviado"));
        when(executionRepository.save(any(WorkflowExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.executeForClients(workflow(), List.of(CLIENT_ID), null, null)).hasSize(1);

        verify(notificationDispatcher).dispatch(any(), any(), any());
    }

    private Workflow workflow() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(WORKFLOW_ID);
        workflow.setCampaignId(20);
        workflow.setWorkflowName("Seguimiento");
        workflow.setActionType(Workflow.ActionType.send_email);
        return workflow;
    }

    private WorkflowExecution executionAt(LocalDateTime executedAt) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setExecutedAt(executedAt);
        return execution;
    }
}
