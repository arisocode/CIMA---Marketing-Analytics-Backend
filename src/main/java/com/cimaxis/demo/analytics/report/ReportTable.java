package com.cimaxis.demo.analytics.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tabla de reporte independiente del formato. Los valores van "crudos"
 * (BigDecimal, Integer, LocalDate, String) y cada renderizador los formatea:
 * el PDF como texto con ReportFormatter y el Excel con formatos numericos reales.
 * Los porcentajes van en escala 0-100 (26.67), como los calcula KpiCalculationService.
 */
public record ReportTable(List<ReportColumn> columns, List<List<Object>> rows) {

    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }

    /** Texto del valor tal como se muestra en el PDF. */
    public static String formatear(ReportColumn.Kind kind, Object valor) {
        if (valor == null) return ReportFormatter.SIN_DATO;
        return switch (kind) {
            case INTEGER -> ReportFormatter.entero(numero(valor));
            case MONEY -> ReportFormatter.moneda(decimal(valor));
            case PERCENT -> ReportFormatter.porcentaje(decimal(valor));
            case DAYS -> ReportFormatter.dias(decimal(valor));
            case DATE -> valor instanceof LocalDate d ? ReportFormatter.fecha(d)
                    : valor instanceof LocalDateTime dt ? ReportFormatter.fecha(dt.toLocalDate()) : valor.toString();
            case DATETIME -> valor instanceof LocalDateTime dt ? ReportFormatter.fechaHora(dt)
                    : valor instanceof LocalDate d ? ReportFormatter.fecha(d) : valor.toString();
            case TEXT -> valor.toString();
        };
    }

    static Number numero(Object valor) {
        if (valor instanceof Number n) return n;
        try {
            return new BigDecimal(valor.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static BigDecimal decimal(Object valor) {
        if (valor instanceof BigDecimal b) return b;
        Number n = numero(valor);
        return n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
    }
}
