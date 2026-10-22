package com.cimaxis.demo.analytics.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.service.KpiType;

/**
 * Los 8 indicadores en el orden del tablero: nombre, tipo de dato, valor en un
 * snapshot y definicion. Las definiciones describen las mismas reglas que
 * aplica KpiCalculationService.
 */
public final class KpiReportCatalog {

    public record Indicator(KpiType type, String label, ReportColumn.Kind kind, String definition,
                            Function<KpiSnapshotDto, BigDecimal> value) {

        public BigDecimal valueOf(KpiSnapshotDto snapshot) {
            return snapshot == null ? null : value.apply(snapshot);
        }
    }

    public static final List<Indicator> INDICATORS = List.of(
            new Indicator(KpiType.NEW_CLIENTS, "Clientes nuevos", ReportColumn.Kind.INTEGER,
                    "Clientes registrados en el mes (según su fecha de alta).",
                    s -> entero(s.getNewClients())),
            new Indicator(KpiType.CLOSED_PROJECTS, "Proyectos cerrados", ReportColumn.Kind.INTEGER,
                    "Proyectos en estado completado o cerrado cuya última actualización cae en el mes.",
                    s -> entero(s.getClosedProjects())),
            new Indicator(KpiType.REVENUE, "Ingresos estimados", ReportColumn.Kind.MONEY,
                    "Suma del valor estimado de las propuestas aprobadas cuya fecha de respuesta cae en el mes "
                            + "(incluye las aprobadas por el cliente desde el portal).",
                    KpiSnapshotDto::getEstimatedRevenue),
            new Indicator(KpiType.ACTIVE_CAMPAIGNS, "Campañas activas", ReportColumn.Kind.INTEGER,
                    "Campañas en estado Activa cuyo rango de fechas se cruza con el mes.",
                    s -> entero(s.getActiveCampaigns())),
            new Indicator(KpiType.CLIENTS_CONTACTED, "Clientes contactados", ReportColumn.Kind.INTEGER,
                    "Clientes distintos con al menos una interacción en el mes. No cuentan los correos que "
                            + "no salieron por el límite diario de envío.",
                    s -> entero(s.getClientsContacted())),
            new Indicator(KpiType.RESPONSE_RATE, "Tasa de respuesta", ReportColumn.Kind.PERCENT,
                    "Clientes que respondieron ÷ clientes contactados × 100. Una respuesta es una interacción "
                            + "con texto de respuesta o de tipo clic, consulta, compra o testimonio.",
                    KpiSnapshotDto::getResponseRate),
            new Indicator(KpiType.AVG_CLOSE_DAYS, "Días promedio de cierre", ReportColumn.Kind.DAYS,
                    "Promedio de días entre el alta del cliente y el cierre de sus proyectos cerrados en el mes.",
                    KpiSnapshotDto::getAvgCloseDays),
            new Indicator(KpiType.PROJECTS_IN_PROGRESS, "Proyectos en curso", ReportColumn.Kind.INTEGER,
                    "Proyectos en estado en curso, en revisión o activo al momento del cálculo (no depende del mes).",
                    s -> entero(s.getProjectsInProgress())));

    private KpiReportCatalog() {
    }

    public static Indicator of(KpiType type) {
        return INDICATORS.stream().filter(i -> i.type() == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Indicador sin definición: " + type));
    }

    private static BigDecimal entero(Integer valor) {
        return valor == null ? null : BigDecimal.valueOf(valor);
    }
}
