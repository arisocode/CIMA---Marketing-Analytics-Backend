package com.cimaxis.demo.marketing.service.whatsapp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;
import com.cimaxis.demo.marketing.service.notifications.DeliveryStatus;

/**
 * WhatsApp como tarea manual: los workflows con send_whatsapp dejan una
 * interaccion channel "whatsapp" en SIMULATED con el mensaje listo. El equipo la
 * envia desde su telefono y la marca SENT_MANUAL; desde ese momento cuenta como
 * contacto (antes no, ver KpiCalculationService.esWhatsappPendiente).
 */
@Service
public class WhatsappTaskService {

    private final MarketingInteractionRepository interactionRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;
    private final ClientRepository clientRepository;

    public WhatsappTaskService(MarketingInteractionRepository interactionRepository,
                               WorkflowExecutionRepository executionRepository,
                               WorkflowRepository workflowRepository,
                               ClientRepository clientRepository) {
        this.interactionRepository = interactionRepository;
        this.executionRepository = executionRepository;
        this.workflowRepository = workflowRepository;
        this.clientRepository = clientRepository;
    }

    /** Pendientes, del mas antiguo al mas reciente. */
    public List<WhatsappPendingItem> pending() {
        List<MarketingInteraction> pendientes = interactionRepository
                .findByChannelIgnoreCaseAndDeliveryStatusOrderByContactDateAsc(
                        KpiCalculationService.CANAL_WHATSAPP, DeliveryStatus.SIMULATED.name());
        if (pendientes == null || pendientes.isEmpty()) return List.of();

        Set<Integer> idsEjecucion = new HashSet<>();
        Set<String> idsCliente = new HashSet<>();
        for (MarketingInteraction i : pendientes) {
            if (i.getExecutionId() != null) idsEjecucion.add(i.getExecutionId());
            if (i.getClientId() != null) idsCliente.add(i.getClientId());
        }
        Map<Integer, WorkflowExecution> ejecuciones = new HashMap<>();
        if (!idsEjecucion.isEmpty()) {
            for (WorkflowExecution e : executionRepository.findAllById(idsEjecucion)) ejecuciones.put(e.getExecutionId(), e);
        }
        Set<Integer> idsWorkflow = new HashSet<>();
        ejecuciones.values().forEach(e -> { if (e.getWorkflowId() != null) idsWorkflow.add(e.getWorkflowId()); });
        Map<Integer, Workflow> workflows = new HashMap<>();
        if (!idsWorkflow.isEmpty()) {
            for (Workflow w : workflowRepository.findAllById(idsWorkflow)) workflows.put(w.getWorkflowId(), w);
        }
        Map<String, Client> clientes = new HashMap<>();
        if (!idsCliente.isEmpty()) {
            for (Client c : clientRepository.findAllById(idsCliente)) clientes.put(c.getClientId(), c);
        }

        return pendientes.stream().map(i -> {
            WorkflowExecution e = i.getExecutionId() != null ? ejecuciones.get(i.getExecutionId()) : null;
            Workflow w = e != null && e.getWorkflowId() != null ? workflows.get(e.getWorkflowId()) : null;
            return new WhatsappPendingItem(i.getInteractionId(), i.getClientId(),
                    CommercialFacts.nombre(clientes.get(i.getClientId())),
                    e != null ? e.getSentMessage() : null, i.getContactDate(),
                    w != null ? w.getWorkflowName() : null);
        }).filter(Objects::nonNull).toList();
    }

    /** Marca el WhatsApp como enviado a mano: SENT_MANUAL y fecha de contacto = ahora. */
    @Transactional
    public WhatsappSentResponse markSent(Integer interactionId, String userId) {
        MarketingInteraction interaction = interactionRepository.findById(interactionId)
                .orElseThrow(() -> ResourceNotFoundException.de("Interacción", interactionId));
        if (!KpiCalculationService.esWhatsappPendiente(interaction)) {
            throw new IllegalStateException("La interacción " + interactionId
                    + " no es un WhatsApp pendiente (canal: " + interaction.getChannel()
                    + ", estado: " + interaction.getDeliveryStatus() + ")");
        }
        LocalDateTime ahora = LocalDateTime.now();
        interaction.setDeliveryStatus(DeliveryStatus.SENT_MANUAL.name());
        interaction.setContactDate(ahora);
        if (userId != null && !userId.isBlank()) interaction.setLoggedBy(userId);
        interactionRepository.save(interaction);
        return new WhatsappSentResponse(interactionId, DeliveryStatus.SENT_MANUAL.name(), ahora);
    }
}
