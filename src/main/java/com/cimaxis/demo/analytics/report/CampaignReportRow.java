package com.cimaxis.demo.analytics.report;

import java.time.LocalDate;

/** Fila del reporte de campañas, con el NOMBRE del cliente (no su id). type y status van en ingles. */
public record CampaignReportRow(String name, String clientName, String type, String status,
                                LocalDate startDate, LocalDate endDate, String platforms, String objective) {
}
