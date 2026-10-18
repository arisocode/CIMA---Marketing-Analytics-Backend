package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un registro (cliente, propuesta, campana o proyecto) que forma parte de un KPI. */
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
