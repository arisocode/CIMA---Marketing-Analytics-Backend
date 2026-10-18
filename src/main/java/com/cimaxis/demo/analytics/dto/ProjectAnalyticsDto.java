package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Proyecto con su cliente. approvedProposalsValue es la suma de las propuestas
 * aprobadas del cliente del proyecto: las propuestas aun no se vinculan a un
 * proyecto concreto.
 */
public record ProjectAnalyticsDto(
        String projectId,
        String name,
        String status,
        String clientId,
        String clientName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal approvedProposalsValue) {
}
