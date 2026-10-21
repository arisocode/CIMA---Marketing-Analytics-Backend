package com.cimaxis.demo.marketing.dto.messages;

import java.time.LocalDateTime;

/** Conversacion con un cliente. unread = mensajes del cliente (INBOUND) sin leer. */
public record MessageThreadResponse(String clientId, String clientName, String plan, String lastMessage,
                                    LocalDateTime lastAt, String lastDirection, long unread) {
}
