package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/** Comparacion de los ocho KPIs de un periodo frente al periodo anterior. */
public record KpiComparisonDto(String period, String previousPeriod, List<KpiDelta> kpis) {

    /** deltaPercent es null cuando el valor anterior es cero. */
    public record KpiDelta(String kpi, BigDecimal current, BigDecimal previous, BigDecimal deltaPercent) {
    }
}
