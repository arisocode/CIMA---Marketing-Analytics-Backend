package com.cimaxis.demo.analytics.report;

/** Columna de una tabla de reporte: titulo, tipo de dato y ancho relativo en el PDF. */
public record ReportColumn(String title, Kind kind, float width) {

    /** Tipo de dato: decide el formato en PDF (texto) y en Excel (formato numerico real). */
    public enum Kind {
        TEXT, INTEGER, MONEY, PERCENT, DAYS, DATE, DATETIME;

        public boolean numeric() {
            return this == INTEGER || this == MONEY || this == PERCENT || this == DAYS;
        }
    }

    public static ReportColumn text(String title, float width) {
        return new ReportColumn(title, Kind.TEXT, width);
    }

    public static ReportColumn of(String title, Kind kind, float width) {
        return new ReportColumn(title, kind, width);
    }
}
