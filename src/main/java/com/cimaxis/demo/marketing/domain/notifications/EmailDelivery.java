package com.cimaxis.demo.marketing.domain.notifications;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de cada intento de correo (workflows, alertas y pruebas). Sirve para
 * contar los correos QUEUED del dia y respetar el limite diario de Brevo.
 * No guarda el cuerpo del mensaje.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "marketing_email_deliveries")
public class EmailDelivery {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Column(name = "subject", length = 255)
    private String subject;

    /** Nombre de DeliveryStatus. Texto (no enum) para no crear un CHECK en la base. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** WORKFLOW, ADMIN_ALERT o TEST. */
    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
