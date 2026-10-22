package com.cimaxis.demo.marketing.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.whatsapp.WhatsappPendingItem;
import com.cimaxis.demo.marketing.dto.whatsapp.WhatsappSentResponse;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;

/** WhatsApp como tarea manual: pendientes y marca SENT_MANUAL. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsappTaskServiceTest {

    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private WorkflowExecutionRepository executionRepository;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private ClientRepository clientRepository;

    private WhatsappTaskService service;
    private MarketingInteraction pendiente;

    @BeforeEach
    void setUp() {
        pendiente = MarketingInteraction.builder().interactionId(70).clientId("c1").executionId(500).campaignId(20)
                .channel("whatsapp").deliveryStatus("SIMULATED").contactDate(LocalDateTime.of(2026, 10, 20, 9, 0))
                .interactionType(MarketingInteraction.InteractionType.message).build();
        when(interactionRepository.findByChannelIgnoreCaseAndDeliveryStatusOrderByContactDateAsc("whatsapp", "SIMULATED"))
                .thenReturn(List.of(pendiente));
        when(executionRepository.findAllById(any())).thenReturn(List.of(WorkflowExecution.builder().executionId(500)
                .workflowId(5).clientId("c1").sentMessage("Hola Ana, ¿retomamos?").build()));
        Workflow w = new Workflow();
        w.setWorkflowId(5);
        w.setWorkflowName("Reactivación");
        when(workflowRepository.findAllById(any())).thenReturn(List.of(w));
        when(clientRepository.findAllById(any())).thenReturn(List.of(Client.builder().clientId("c1").additionalInfo("Ana").build()));
        when(interactionRepository.findById(70)).thenReturn(Optional.of(pendiente));
        service = new WhatsappTaskService(interactionRepository, executionRepository, workflowRepository, clientRepository);
    }

    @Test
    @DisplayName("Pendientes con cliente, mensaje preparado y nombre del workflow")
    void pendientes() {
        WhatsappPendingItem item = service.pending().get(0);

        assertThat(item.interactionId()).isEqualTo(70);
        assertThat(item.clientName()).isEqualTo("Ana");
        assertThat(item.message()).isEqualTo("Hola Ana, ¿retomamos?");
        assertThat(item.workflowName()).isEqualTo("Reactivación");
        assertThat(item.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0));
    }

    @Test
    @DisplayName("Un WhatsApp pendiente no es contacto; al marcarlo SENT_MANUAL sí")
    void marcarEnviado() {
        assertThat(KpiCalculationService.esContacto(pendiente)).isFalse();

        WhatsappSentResponse r = service.markSent(70, "worker-1");

        assertThat(r.deliveryStatus()).isEqualTo("SENT_MANUAL");
        assertThat(pendiente.getDeliveryStatus()).isEqualTo("SENT_MANUAL");
        assertThat(pendiente.getLoggedBy()).isEqualTo("worker-1");
        assertThat(KpiCalculationService.esContacto(pendiente)).isTrue();
        verify(interactionRepository).save(pendiente);
    }

    @Test
    @DisplayName("Marcar algo que no es un WhatsApp pendiente responde 409; inexistente 404")
    void validaciones() {
        pendiente.setDeliveryStatus("SENT_MANUAL");
        assertThatThrownBy(() -> service.markSent(70, "worker-1")).isInstanceOf(IllegalStateException.class);
        verify(interactionRepository, never()).save(any());

        assertThatThrownBy(() -> service.markSent(99, "worker-1")).isInstanceOf(ResourceNotFoundException.class);
    }
}
