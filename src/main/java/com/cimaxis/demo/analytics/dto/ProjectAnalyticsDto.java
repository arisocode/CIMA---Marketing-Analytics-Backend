package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
