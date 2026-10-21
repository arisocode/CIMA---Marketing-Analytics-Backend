package com.cimaxis.demo.marketing.dto.messages;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensaje enviado por CIMA. emailDeliveryStatus (QUEUED, FAILED, SIMULATED o SKIPPED_LIMIT)
 * y emailDetail solo vienen cuando se pidio alsoEmail=true.
 */
public record StaffMessageResponse(UUID messageId, String direction, String body, LocalDateTime createdAt,
                                   LocalDateTime readAt, String authorName,
                                   String emailDeliveryStatus, String emailDetail) {
}
