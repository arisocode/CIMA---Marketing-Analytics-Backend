package com.cimaxis.demo.marketing.dto.whatsapp;

import java.time.LocalDateTime;

/** WhatsApp preparado por un workflow que el equipo debe enviar a mano. */
public record WhatsappPendingItem(Integer interactionId, String clientId, String clientName, String message,
                                  LocalDateTime createdAt, String workflowName) {
}
