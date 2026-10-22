package com.cimaxis.demo.marketing.service.notifications;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.messages.MarketingMessage;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.repository.messages.MarketingMessageRepository;

/**
 * Traduce el action_type de un workflow en una accion concreta sobre un canal.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    public static final String CANAL_PORTAL = "portal";
    public static final String AUTOR_PORTAL = "Equipo CIMA";

    private final EmailService emailService;
    private final CrmIntegrationService crmIntegrationService;
    private final MarketingMessageRepository messageRepository;

    @Value("${cimaxis.notifications.admin-recipient:}")
    private String adminRecipient;

    public NotificationDispatcher(EmailService emailService,
                                  CrmIntegrationService crmIntegrationService,
                                  MarketingMessageRepository messageRepository) {
        this.emailService = emailService;
        this.crmIntegrationService = crmIntegrationService;
        this.messageRepository = messageRepository;
    }

    /** Despacha usando el nombre del workflow como asunto del correo. */
    public DispatchResult dispatch(Workflow workflow,
                                   Map<String, Object> clientData,
                                   String message) {
        return dispatch(workflow, clientData, workflow.getWorkflowName(), message);
    }

    /**
     * Despacha la accion del workflow. subject solo aplica a send_email (por
     * ejemplo, el asunto de la plantilla del workflow).
     */
    public DispatchResult dispatch(Workflow workflow,
                                   Map<String, Object> clientData,
                                   String subject,
                                   String message) {

        return switch (workflow.getActionType()) {

            case send_email -> emailService.send(
                    crmIntegrationService.extractClientEmail(clientData),
                    subject != null && !subject.isBlank() ? subject : workflow.getWorkflowName(),
                    message,
                    EmailService.ORIGEN_WORKFLOW);

            // No hay integracion con WhatsApp: el mensaje queda registrado y se marca como simulado.
            case send_whatsapp -> DispatchResult.simulated("whatsapp",
                    "Mensaje preparado para WhatsApp (no se envía automáticamente): " + message);

            case log_followup -> {
                log.info("Seguimiento interno registrado por workflow {}: {}",
                        workflow.getWorkflowName(), message);
                yield DispatchResult.ok("internal", "Seguimiento registrado en el sistema");
            }

            case notify_admin -> notifyAdmin("Alerta administrativa: " + workflow.getWorkflowName(), message);

            case send_portal_message -> publicarEnPortal(clientData, message);
        };
    }

    /**
     * Publica el mensaje en la conversacion del portal del cliente (OUTBOUND, autor
     * "Equipo CIMA"). El cliente lo ve en /me/messages.
     */
    private DispatchResult publicarEnPortal(Map<String, Object> clientData, String message) {
        String clientId = clientData != null ? crmIntegrationService.extractClientId(clientData) : null;
        if (clientId == null || clientId.isBlank()) {
            return DispatchResult.deliveryFailed(CANAL_PORTAL, "No se identificó el cliente para el mensaje del portal");
        }
        String cuerpo = message == null || message.isBlank() ? "Mensaje de CIMA" : message.strip();
        if (cuerpo.length() > MarketingMessage.LONGITUD_MAXIMA_CUERPO) {
            cuerpo = cuerpo.substring(0, MarketingMessage.LONGITUD_MAXIMA_CUERPO);
        }
        messageRepository.save(MarketingMessage.builder()
                .messageId(UUID.randomUUID())
                .clientId(clientId.trim())
                .direction(MarketingMessage.Direction.OUTBOUND)
                .body(cuerpo)
                .authorName(AUTOR_PORTAL)
                .createdAt(LocalDateTime.now(EmailService.ZONA_CIMA))
                .build());
        return DispatchResult.ok(CANAL_PORTAL, "Mensaje publicado en el portal del cliente");
    }

    /**
     * Correo al destinatario administrativo (ADMIN_NOTIFICATION_RECIPIENT). Lo usan
     * los workflows notify_admin y el portal del cliente (decisiones de propuestas).
     */
    public DispatchResult notifyAdmin(String subject, String message) {
        if (adminRecipient == null || adminRecipient.isBlank()) {
            log.error("No se entrego la alerta administrativa '{}': falta configurar ADMIN_NOTIFICATION_RECIPIENT", subject);
            return DispatchResult.deliveryFailed("admin_notification",
                    "No hay destinatario administrativo configurado");
        }
        return emailService.send(adminRecipient.trim(), subject, message, EmailService.ORIGEN_ALERTA)
                .withChannel("admin_notification");
    }
}
