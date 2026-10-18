package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/** Comparacion de los ocho KPIs de un periodo frente al periodo anterior. */
public record KpiComparisonDto(String period, String previousPeriod, List<KpiDelta> kpis) {

    public record KpiDelta(String kpi, BigDecimal current, BigDecimal previous, BigDecimal deltaPercent) {
    }
}
