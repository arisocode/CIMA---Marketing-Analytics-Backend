package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KpiDetailItemDto(
        String id,
        String title,
        String subtitle,
        LocalDateTime date,
        BigDecimal value,
        String status,
        String clientId,
        String clientName) {
}
