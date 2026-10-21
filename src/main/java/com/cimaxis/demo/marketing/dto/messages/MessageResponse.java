package com.cimaxis.demo.marketing.dto.messages;

import java.time.LocalDateTime;
import java.util.UUID;

/** Mensaje de la conversacion. direction: OUTBOUND (CIMA a cliente) o INBOUND (cliente a CIMA). */
public record MessageResponse(UUID messageId, String direction, String body, LocalDateTime createdAt,
                              LocalDateTime readAt, String authorName) {
}
