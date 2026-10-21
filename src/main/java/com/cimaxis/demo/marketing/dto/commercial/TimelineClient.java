package com.cimaxis.demo.marketing.dto.commercial;

import java.time.LocalDateTime;

/** email = contactInfo del cliente (el modelo no tiene telefono). */
public record TimelineClient(String clientId, String name, String email, String plan,
                             LocalDateTime createdAt, LocalDateTime lastContact, int score) {
}
