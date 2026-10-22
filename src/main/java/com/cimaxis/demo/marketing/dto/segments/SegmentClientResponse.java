package com.cimaxis.demo.marketing.dto.segments;

import java.time.LocalDateTime;

/** Cliente de una audiencia: nombre, plan, ultimo contacto y puntaje de interes. */
public record SegmentClientResponse(String clientId, String name, String plan, LocalDateTime lastContact, int score) {
}
