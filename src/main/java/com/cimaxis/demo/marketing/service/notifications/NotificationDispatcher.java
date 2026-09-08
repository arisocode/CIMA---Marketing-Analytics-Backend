package com.cimaxis.demo.marketing.service.notifications;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.integration.crm.service.CrmIntegrationService;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;

/**
 * Traduce el action_type de un workflow en una accion concreta sobre un canal.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final EmailService emailService;
    private final CrmIntegrationService crmIntegrationService;

    @Value("${cimaxis.notifications.admin-recipient:}")
    private String adminRecipient;

    public NotificationDispatcher(EmailService emailService,
                                  CrmIntegrationService crmIntegrationService) {
        this.emailService = emailService;
        this.crmIntegrationService = crmIntegrationService;
    }

    public DispatchResult dispatch(Workflow workflow,
                                   Map<String, Object> clientData,
                                   String message) {

        return switch (workflow.getActionType()) {

            case send_email -> emailService.send(
                    crmIntegrationService.extractClientEmail(clientData),
                    workflow.getWorkflowName(),
                    message);

            case send_whatsapp -> DispatchResult.ok("whatsapp",
                    "Mensaje preparado para envio por WhatsApp: " + message);

            case log_followup -> {
                log.info("Seguimiento interno registrado por workflow {}: {}",
                        workflow.getWorkflowName(), message);
                yield DispatchResult.ok("internal", "Seguimiento registrado en el sistema");
            }

            case notify_admin -> {
                if (adminRecipient == null || adminRecipient.isBlank()) {
                    log.error("No se entrego la alerta administrativa del workflow {}: falta configurar ADMIN_NOTIFICATION_RECIPIENT",
                            workflow.getWorkflowName());
                    yield DispatchResult.failed("admin_notification",
                            "No hay destinatario administrativo configurado");
                }

                DispatchResult emailResult = emailService.send(
                        adminRecipient.trim(),
                        "Alerta administrativa: " + workflow.getWorkflowName(),
                        message);
                yield new DispatchResult("admin_notification", emailResult.delivered(), emailResult.detail());
            }
        };
    }
}
