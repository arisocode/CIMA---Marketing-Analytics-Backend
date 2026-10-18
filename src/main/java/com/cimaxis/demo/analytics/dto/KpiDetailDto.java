package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record KpiDetailDto(
        String kpi,
        String period,
        int total,
        BigDecimal kpiValue,
        List<KpiDetailItemDto> items) {
}
