package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de un KPI. total es el numero de registros listados; kpiValue es el valor
 * que muestra la tarjeta (conteo, suma de ingresos, porcentaje o promedio de dias).
 */
public record KpiDetailDto(
        String kpi,
        String period,
        int total,
        BigDecimal kpiValue,
        List<KpiDetailItemDto> items) {
}
