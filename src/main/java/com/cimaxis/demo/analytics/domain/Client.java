package com.cimaxis.demo.analytics.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "CLIENTS")
public class Client {

    /**
     * Entidad que mapea la tabla `CLIENTS`.
     * Contiene información de negocio relacionada con clientes finales.
     */

    @Id
    @Column(name = "client_id", length = 36)
    private String clientId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "contact_info", length = 255)
    private String contactInfo;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    /**
     * Nombre del plan comercial (tabla marketing_plans). Es texto, no una lista
     * fija: CIMA puede crear planes nuevos desde /api/v1/marketing/plans.
     */
    @Column(name = "plan", length = 60)
    private String plan;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
