package com.cimaxis.demo.marketing.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private EmailService emailService;
    @Mock private CrmIntegrationService crmIntegrationService;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(emailService, crmIntegrationService);
    }

    @Test
    void failNotifyAdminWhenNoRecipientIsConfigured() {
        ReflectionTestUtils.setField(dispatcher, "adminRecipient", " ");

        DispatchResult result = dispatcher.dispatch(notifyAdminWorkflow(), Map.of(), "Revisar seguimiento");

        assertThat(result.delivered()).isFalse();
        assertThat(result.channel()).isEqualTo("admin_notification");
        verifyNoInteractions(emailService);
    }

    @Test
    void sendNotifyAdminToTheConfiguredOperationalMailbox() {
        ReflectionTestUtils.setField(dispatcher, "adminRecipient", "operaciones@example.test");
        when(emailService.send("operaciones@example.test", "Alerta administrativa: Seguimiento", "Revisar seguimiento"))
                .thenReturn(DispatchResult.ok("email", "Enviado"));

        DispatchResult result = dispatcher.dispatch(notifyAdminWorkflow(), Map.of(), "Revisar seguimiento");

        assertThat(result).isEqualTo(new DispatchResult("admin_notification", true, "Enviado"));
        verify(emailService).send("operaciones@example.test", "Alerta administrativa: Seguimiento", "Revisar seguimiento");
    }

    private Workflow notifyAdminWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("Seguimiento");
        workflow.setActionType(Workflow.ActionType.notify_admin);
        return workflow;
    }
}
