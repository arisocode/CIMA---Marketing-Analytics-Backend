package com.cimaxis.demo.marketing.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.messages.MarketingMessage;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.repository.messages.MarketingMessageRepository;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private EmailService emailService;
    @Mock private CrmIntegrationService crmIntegrationService;
    @Mock private MarketingMessageRepository messageRepository;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(emailService, crmIntegrationService, messageRepository);
    }

    @Test
    void failNotifyAdminWhenNoRecipientIsConfigured() {
        ReflectionTestUtils.setField(dispatcher, "adminRecipient", " ");

        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.notify_admin), Map.of(), "Revisar seguimiento");

        assertThat(result.delivered()).isFalse();
        assertThat(result.channel()).isEqualTo("admin_notification");
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        verifyNoInteractions(emailService);
    }

    @Test
    void sendNotifyAdminToTheConfiguredOperationalMailboxKeepingTheDeliveryStatus() {
        ReflectionTestUtils.setField(dispatcher, "adminRecipient", "operaciones@example.test");
        when(emailService.send("operaciones@example.test", "Alerta administrativa: Seguimiento",
                "Revisar seguimiento", EmailService.ORIGEN_ALERTA))
                .thenReturn(DispatchResult.queued("email", "Encolado"));

        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.notify_admin), Map.of(), "Revisar seguimiento");

        assertThat(result).isEqualTo(new DispatchResult("admin_notification", true, "Encolado", DeliveryStatus.QUEUED));
    }

    @Test
    void notifyAdminDirectoUsaElAsuntoIndicado() {
        ReflectionTestUtils.setField(dispatcher, "adminRecipient", "operaciones@example.test");
        when(emailService.send("operaciones@example.test", "Propuesta aceptada por Ana (#3)",
                "Detalle", EmailService.ORIGEN_ALERTA))
                .thenReturn(DispatchResult.queued("email", "Encolado"));

        DispatchResult result = dispatcher.notifyAdmin("Propuesta aceptada por Ana (#3)", "Detalle");

        assertThat(result).isEqualTo(new DispatchResult("admin_notification", true, "Encolado", DeliveryStatus.QUEUED));
    }

    @Test
    void sendEmailUsesTheSubjectFromTheTemplate() {
        Map<String, Object> cliente = Map.of("email", "ana@test.com");
        when(crmIntegrationService.extractClientEmail(cliente)).thenReturn("ana@test.com");
        when(emailService.send("ana@test.com", "Tu propuesta te espera", "Hola Ana", EmailService.ORIGEN_WORKFLOW))
                .thenReturn(DispatchResult.queued("email", "Encolado"));

        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.send_email), cliente,
                "Tu propuesta te espera", "Hola Ana");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.QUEUED);
        verify(emailService).send("ana@test.com", "Tu propuesta te espera", "Hola Ana", EmailService.ORIGEN_WORKFLOW);
    }

    @Test
    void sendEmailFallsBackToTheWorkflowNameAsSubject() {
        when(crmIntegrationService.extractClientEmail(Map.of())).thenReturn("ana@test.com");
        when(emailService.send("ana@test.com", "Seguimiento", "Hola", EmailService.ORIGEN_WORKFLOW))
                .thenReturn(DispatchResult.simulated("email", "Simulado"));

        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.send_email), Map.of(), "Hola");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.SIMULATED);
    }

    @Test
    void whatsappIsMarkedAsSimulatedBecauseThereIsNoIntegration() {
        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.send_whatsapp), Map.of(), "Hola");

        assertThat(result.delivered()).isTrue();
        assertThat(result.channel()).isEqualTo("whatsapp");
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.SIMULATED);
        verifyNoInteractions(emailService);
    }

    @Test
    void sendPortalMessageCreatesAnOutboundMessageFromEquipoCima() {
        Map<String, Object> cliente = Map.of("id", "c-1");
        when(crmIntegrationService.extractClientId(cliente)).thenReturn("c-1");

        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.send_portal_message), cliente,
                "Asunto", "Hola Ana, tu propuesta te espera");

        assertThat(result.delivered()).isTrue();
        assertThat(result.channel()).isEqualTo("portal");
        ArgumentCaptor<MarketingMessage> captor = ArgumentCaptor.forClass(MarketingMessage.class);
        verify(messageRepository).save(captor.capture());
        MarketingMessage mensaje = captor.getValue();
        assertThat(mensaje.getClientId()).isEqualTo("c-1");
        assertThat(mensaje.getDirection()).isEqualTo(MarketingMessage.Direction.OUTBOUND);
        assertThat(mensaje.getAuthorName()).isEqualTo("Equipo CIMA");
        assertThat(mensaje.getBody()).isEqualTo("Hola Ana, tu propuesta te espera");
        verifyNoInteractions(emailService);
    }

    @Test
    void sendPortalMessageWithoutClientFails() {
        DispatchResult result = dispatcher.dispatch(workflow(Workflow.ActionType.send_portal_message), Map.of(), "Hola");

        assertThat(result.delivered()).isFalse();
        verifyNoInteractions(messageRepository);
    }

    private Workflow workflow(Workflow.ActionType accion) {
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("Seguimiento");
        workflow.setActionType(accion);
        return workflow;
    }
}
