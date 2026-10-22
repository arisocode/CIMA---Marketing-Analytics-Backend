package com.cimaxis.demo.analytics.service;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.dto.InventoryAlertDto;
import com.cimaxis.demo.analytics.dto.KpiDetailDto;
import com.cimaxis.demo.analytics.dto.KpiDetailItemDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.report.CampaignReportRow;
import com.cimaxis.demo.analytics.report.ExcelReportRenderer;
import com.cimaxis.demo.analytics.report.KpiReportCatalog;
import com.cimaxis.demo.analytics.report.PdfReportRenderer;
import com.cimaxis.demo.analytics.report.ReportBranding;
import com.cimaxis.demo.analytics.report.ReportColumn;
import com.cimaxis.demo.analytics.report.ReportColumn.Kind;
import com.cimaxis.demo.analytics.report.ReportFormatter;
import com.cimaxis.demo.analytics.report.ReportLogo;
import com.cimaxis.demo.analytics.report.ReportTable;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;

import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

/**
 * Reportes descargables de CIMAxis (PDF con OpenPDF y Excel con Apache POI),
 * con formato de entregable para la gerencia: marca CIMA, valores en español
 * (ReportFormatter) y numeros reales en Excel. Las rutas de descarga del
 * AnalyticsController no cambian.
 */
@Service
public class ReportExportService {

    private final ReportLogo logo;
    private final Clock clock;

    @Autowired
    public ReportExportService(ReportLogo logo) {
        this(logo, Clock.system(ReportFormatter.ZONA_CIMA));
    }

    public ReportExportService(ReportLogo logo, Clock clock) {
        this.logo = logo;
        this.clock = clock;
    }

    // ==================================================================== indicadores

    public byte[] kpisToPdf(List<KpiSnapshotDto> snapshots) {
        return kpisToPdf(snapshots, null);
    }

    /**
     * Reporte de indicadores. anterior (opcional) es el periodo previo para las
     * variaciones cuando la lista trae un solo periodo; si es null se compara con el
     * siguiente periodo mas antiguo de la lista.
     */
    public byte[] kpisToPdf(List<KpiSnapshotDto> snapshots, KpiSnapshotDto anterior) {
        List<KpiSnapshotDto> historial = ordenarPorPeriodo(snapshots);
        String subtitulo = subtituloPeriodos(historial);
        return PdfReportRenderer.render("Reporte de indicadores", subtitulo, logoBytes(), doc -> {
            if (historial.isEmpty()) {
                doc.add(PdfReportRenderer.sinDatos());
                return;
            }
            KpiSnapshotDto ultimo = historial.get(0);
            KpiSnapshotDto previo = anterior != null ? anterior : (historial.size() > 1 ? historial.get(1) : null);
            String comparacion = previo != null ? "frente a " + ReportFormatter.periodo(previo.getPeriod()) : null;

            doc.add(PdfReportRenderer.seccion("Resumen del último período · " + ReportFormatter.periodo(ultimo.getPeriod())));
            PdfPTable tarjetas = new PdfPTable(4);
            tarjetas.setWidthPercentage(100);
            tarjetas.setSpacingAfter(6);
            for (KpiReportCatalog.Indicator indicador : KpiReportCatalog.INDICATORS) {
                BigDecimal actual = indicador.valueOf(ultimo);
                BigDecimal antes = indicador.valueOf(previo);
                String variacion = ReportFormatter.variacion(actual, antes);
                PdfPCell tarjeta = PdfReportRenderer.tarjeta(indicador.label(),
                        ReportTable.formatear(indicador.kind(), actual != null ? actual : BigDecimal.ZERO),
                        variacion, colorVariacion(variacion),
                        "sin dato".equals(variacion) ? null : comparacion);
                PdfPTable envoltura = new PdfPTable(1);
                envoltura.setWidthPercentage(100);
                envoltura.addCell(tarjeta);
                PdfPCell hueco = new PdfPCell(envoltura);
                hueco.setBorder(Rectangle.NO_BORDER);
                hueco.setPadding(4);
                tarjetas.addCell(hueco);
            }
            doc.add(tarjetas);
            doc.add(PdfReportRenderer.nota("La variación compara cada indicador con el período anterior. "
                    + "\"sin dato\" indica que el período anterior es 0 o no está disponible."));

            doc.add(PdfReportRenderer.seccion("Historial por período"));
            doc.add(PdfReportRenderer.tabla(tablaHistorial(historial, false)));

            doc.add(PdfReportRenderer.bloque(PdfReportRenderer.seccion("Cómo leer estos indicadores"),
                    PdfReportRenderer.definiciones(definiciones())));
        });
    }

    public byte[] kpisToExcel(List<KpiSnapshotDto> snapshots) {
        List<KpiSnapshotDto> historial = ordenarPorPeriodo(snapshots);
        ReportTable definiciones = new ReportTable(
                List.of(ReportColumn.text("Indicador", 1.2f), ReportColumn.text("Definición", 4f)),
                definiciones().stream().map(par -> List.<Object>of(par[0], par[1])).toList());
        return ExcelReportRenderer.render(List.of(
                new ExcelReportRenderer.Sheet("Indicadores", "Reporte de indicadores - CIMAxis",
                        subtituloPeriodos(historial), tablaHistorial(historial, true), false),
                new ExcelReportRenderer.Sheet("Definiciones", "Cómo leer estos indicadores",
                        "Reglas de cálculo de cada indicador (mismas del tablero de CIMAxis)", definiciones, true)));
    }

    private ReportTable tablaHistorial(List<KpiSnapshotDto> historial, boolean conFechaDeCalculo) {
        List<ReportColumn> columnas = new ArrayList<>();
        columnas.add(ReportColumn.text("Período", 1.35f));
        for (KpiReportCatalog.Indicator i : KpiReportCatalog.INDICATORS) {
            columnas.add(ReportColumn.of(i.label(), i.kind(), i.kind() == Kind.MONEY ? 1.4f : 1f));
        }
        if (conFechaDeCalculo) columnas.add(ReportColumn.of("Calculado el", Kind.DATETIME, 1.6f));

        List<List<Object>> filas = new ArrayList<>();
        for (KpiSnapshotDto s : historial) {
            List<Object> fila = new ArrayList<>();
            fila.add(ReportFormatter.periodo(s.getPeriod()));
            for (KpiReportCatalog.Indicator i : KpiReportCatalog.INDICATORS) {
                fila.add(i.valueOf(s) != null ? i.valueOf(s) : BigDecimal.ZERO);
            }
            if (conFechaDeCalculo) fila.add(s.getCalculatedAt());
            filas.add(fila);
        }
        return new ReportTable(columnas, filas);
    }

    private static List<String[]> definiciones() {
        return KpiReportCatalog.INDICATORS.stream()
                .map(i -> new String[] {i.label(), i.definition()})
                .toList();
    }

    // ==================================================================== campañas

    public byte[] campaignsToPdf(List<CampaignReportRow> campanas) {
        List<CampaignReportRow> filas = campanas != null ? campanas : List.of();
        String subtitulo = subtituloCampanas(filas);
        return PdfReportRenderer.render("Reporte de campañas", subtitulo, logoBytes(), doc -> {
            if (filas.isEmpty()) {
                doc.add(PdfReportRenderer.sinDatos());
                return;
            }
            doc.add(PdfReportRenderer.seccion("Resumen"));
            PdfPTable resumen = new PdfPTable(2);
            resumen.setWidthPercentage(70);
            resumen.setHorizontalAlignment(Element.ALIGN_LEFT);
            resumen.setWidths(new float[] {1f, 1f});
            resumen.addCell(sinBorde(PdfReportRenderer.tabla(conteo("Estado", porEstado(filas)))));
            resumen.addCell(sinBorde(PdfReportRenderer.tabla(conteo("Tipo", porTipo(filas)))));
            doc.add(resumen);

            doc.add(PdfReportRenderer.seccion("Detalle de campañas"));
            doc.add(PdfReportRenderer.tabla(tablaCampanas(filas)));
        });
    }

    public byte[] campaignsToExcel(List<CampaignReportRow> campanas) {
        List<CampaignReportRow> filas = campanas != null ? campanas : List.of();
        List<List<Object>> resumen = new ArrayList<>();
        porEstado(filas).forEach((k, v) -> resumen.add(List.of("Estado", k, v)));
        porTipo(filas).forEach((k, v) -> resumen.add(List.of("Tipo", k, v)));
        ReportTable tablaResumen = new ReportTable(List.of(ReportColumn.text("Agrupación", 1f),
                ReportColumn.text("Valor", 1.5f), ReportColumn.of("Campañas", Kind.INTEGER, 1f)), resumen);
        return ExcelReportRenderer.render(List.of(
                new ExcelReportRenderer.Sheet("Campañas", "Reporte de campañas - CIMAxis",
                        subtituloCampanas(filas), tablaCampanas(filas), false),
                new ExcelReportRenderer.Sheet("Resumen", "Campañas por estado y por tipo",
                        subtituloCampanas(filas), tablaResumen, false)));
    }

    private ReportTable tablaCampanas(List<CampaignReportRow> filas) {
        List<ReportColumn> columnas = List.of(
                ReportColumn.text("Campaña", 1.6f), ReportColumn.text("Cliente", 1.4f),
                ReportColumn.text("Tipo", 1.5f), ReportColumn.text("Estado", 0.85f),
                ReportColumn.of("Inicio", Kind.DATE, 2.05f), ReportColumn.of("Fin", Kind.DATE, 2.05f),
                ReportColumn.text("Plataformas", 1.2f), ReportColumn.text("Objetivo", 2.4f));
        List<List<Object>> datos = filas.stream()
                .map(c -> Arrays.<Object>asList(c.name(), c.clientName(), ReportFormatter.tipoCampana(c.type()),
                        ReportFormatter.estadoCampana(c.status()), c.startDate(), c.endDate(),
                        c.platforms(), ReportFormatter.recortar(c.objective(), 80)))
                .toList();
        return new ReportTable(columnas, datos);
    }

    private static Map<String, Long> porEstado(List<CampaignReportRow> filas) {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (Campaign.CampaignStatus estado : List.of(Campaign.CampaignStatus.Active, Campaign.CampaignStatus.Paused,
                Campaign.CampaignStatus.Draft, Campaign.CampaignStatus.Completed, Campaign.CampaignStatus.Cancelled)) {
            long n = filas.stream().filter(f -> estado.name().equalsIgnoreCase(f.status())).count();
            if (n > 0) conteo.put(ReportFormatter.estadoCampana(estado.name()), n);
        }
        long sinEstado = filas.stream().filter(f -> f.status() == null).count();
        if (sinEstado > 0) conteo.put("Sin estado", sinEstado);
        return conteo;
    }

    private static Map<String, Long> porTipo(List<CampaignReportRow> filas) {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (Campaign.CampaignType tipo : Campaign.CampaignType.values()) {
            long n = filas.stream().filter(f -> tipo.name().equalsIgnoreCase(f.type())).count();
            if (n > 0) conteo.put(ReportFormatter.tipoCampana(tipo.name()), n);
        }
        return conteo;
    }

    private static ReportTable conteo(String titulo, Map<String, Long> valores) {
        List<List<Object>> filas = new ArrayList<>();
        valores.forEach((k, v) -> filas.add(List.of(k, v)));
        filas.add(List.of("Total", valores.values().stream().mapToLong(Long::longValue).sum()));
        return new ReportTable(List.of(ReportColumn.text(titulo, 2f), ReportColumn.of("Campañas", Kind.INTEGER, 1f)),
                filas);
    }

    private String subtituloCampanas(List<CampaignReportRow> filas) {
        LocalDate desde = filas.stream().map(CampaignReportRow::startDate).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate hasta = filas.stream().map(CampaignReportRow::endDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        String rango = desde == null ? "Sin fechas registradas"
                : "Del " + ReportFormatter.fecha(desde) + (hasta != null ? " al " + ReportFormatter.fecha(hasta) : " en adelante");
        return ReportFormatter.entero(filas.size()) + (filas.size() == 1 ? " campaña · " : " campañas · ")
                + rango + " · " + generado();
    }

    // ==================================================================== detalle de un indicador

    public byte[] kpiDetailToPdf(KpiDetailDto detalle) {
        KpiReportCatalog.Indicator indicador = KpiReportCatalog.of(KpiType.fromKey(detalle.kpi()));
        String subtitulo = "Período: " + ReportFormatter.periodo(detalle.period()) + " · " + generado();
        ReportTable tabla = tablaDetalle(indicador.type(), detalle.items());
        return PdfReportRenderer.render("Detalle: " + indicador.label(), subtitulo, logoBytes(), doc -> {
            doc.add(PdfReportRenderer.destacado("Total del indicador en " + ReportFormatter.periodo(detalle.period()),
                    ReportTable.formatear(indicador.kind(), detalle.kpiValue() != null ? detalle.kpiValue() : BigDecimal.ZERO),
                    ReportFormatter.entero(detalle.total()) + (detalle.total() == 1 ? " registro" : " registros")));
            if (tabla.isEmpty()) {
                doc.add(PdfReportRenderer.sinDatos());
                return;
            }
            doc.add(PdfReportRenderer.tabla(tabla));
            doc.add(PdfReportRenderer.seccion("Cómo se calcula"));
            doc.add(PdfReportRenderer.nota(indicador.definition()));
        });
    }

    public byte[] kpiDetailToExcel(KpiDetailDto detalle) {
        KpiReportCatalog.Indicator indicador = KpiReportCatalog.of(KpiType.fromKey(detalle.kpi()));
        String subtitulo = "Período: " + ReportFormatter.periodo(detalle.period()) + " · Total: "
                + ReportTable.formatear(indicador.kind(), detalle.kpiValue() != null ? detalle.kpiValue() : BigDecimal.ZERO)
                + " · " + generado();
        return ExcelReportRenderer.render(List.of(new ExcelReportRenderer.Sheet("Detalle",
                "Detalle: " + indicador.label() + " - CIMAxis", subtitulo,
                tablaDetalle(indicador.type(), detalle.items()), false)));
    }

    /** Columnas segun el indicador: cada uno muestra lo que significa su registro. */
    static ReportTable tablaDetalle(KpiType tipo, List<KpiDetailItemDto> items) {
        List<KpiDetailItemDto> registros = items != null ? items : List.of();
        List<ReportColumn> columnas = switch (tipo) {
            case NEW_CLIENTS -> List.of(ReportColumn.text("Cliente", 2.2f), ReportColumn.text("Plan", 1.2f),
                    ReportColumn.of("Fecha de alta", Kind.DATE, 1.4f));
            case CLOSED_PROJECTS -> List.of(ReportColumn.text("Proyecto", 2.2f), ReportColumn.text("Cliente", 1.8f),
                    ReportColumn.of("Fecha de cierre", Kind.DATE, 1.4f), ReportColumn.text("Estado", 1f));
            case REVENUE -> List.of(ReportColumn.text("Propuesta", 2.4f), ReportColumn.text("Cliente", 1.8f),
                    ReportColumn.of("Fecha de aprobación", Kind.DATE, 1.4f), ReportColumn.of("Valor", Kind.MONEY, 1.3f),
                    ReportColumn.text("Estado", 1f));
            case ACTIVE_CAMPAIGNS -> List.of(ReportColumn.text("Campaña", 2.2f), ReportColumn.text("Cliente", 1.8f),
                    ReportColumn.text("Tipo", 1.3f), ReportColumn.of("Inicio", Kind.DATE, 1.4f),
                    ReportColumn.text("Estado", 1f));
            case CLIENTS_CONTACTED -> List.of(ReportColumn.text("Cliente", 2.2f), ReportColumn.text("Plan", 1.2f),
                    ReportColumn.of("Último contacto", Kind.DATE, 1.4f), ReportColumn.of("Interacciones", Kind.INTEGER, 1f));
            case RESPONSE_RATE -> List.of(ReportColumn.text("Cliente", 2.2f), ReportColumn.text("Plan", 1.2f),
                    ReportColumn.of("Última respuesta", Kind.DATE, 1.4f), ReportColumn.of("Respuestas", Kind.INTEGER, 1f));
            case AVG_CLOSE_DAYS -> List.of(ReportColumn.text("Proyecto", 2.2f), ReportColumn.text("Cliente", 1.8f),
                    ReportColumn.of("Fecha de cierre", Kind.DATE, 1.4f), ReportColumn.of("Días de cierre", Kind.DAYS, 1.1f));
            case PROJECTS_IN_PROGRESS -> List.of(ReportColumn.text("Proyecto", 2.2f), ReportColumn.text("Cliente", 1.8f),
                    ReportColumn.of("Última actualización", Kind.DATE, 1.4f), ReportColumn.text("Estado", 1f));
        };
        List<List<Object>> filas = registros.stream().map(i -> fila(tipo, i)).toList();
        return new ReportTable(columnas, filas);
    }

    private static List<Object> fila(KpiType tipo, KpiDetailItemDto i) {
        String cliente = i.clientName() != null ? i.clientName() : "Sin cliente";
        return switch (tipo) {
            case NEW_CLIENTS -> Arrays.asList(i.title(), i.subtitle(), i.date());
            case CLOSED_PROJECTS, PROJECTS_IN_PROGRESS -> Arrays.asList(i.title(), cliente, i.date(),
                    ReportFormatter.estadoProyecto(i.status()));
            case REVENUE -> Arrays.asList(i.title(), cliente, i.date(), i.value(), ReportFormatter.estado(i.status()));
            case ACTIVE_CAMPAIGNS -> Arrays.asList(i.title(), cliente, ReportFormatter.tipoCampana(i.subtitle()),
                    i.date(), ReportFormatter.estado(i.status()));
            case CLIENTS_CONTACTED, RESPONSE_RATE -> Arrays.asList(i.title(),
                    i.status() != null ? i.status() : "Sin plan", i.date(), i.value());
            case AVG_CLOSE_DAYS -> Arrays.asList(i.title(), cliente, i.date(), i.value());
        };
    }

    // ==================================================================== inventario

    public byte[] lowStockToPdf(List<InventoryAlertDto> alertas) {
        ReportTable tabla = tablaInventario(alertas);
        return PdfReportRenderer.render("Alertas de inventario", subtituloInventario(tabla), logoBytes(), doc -> {
            if (tabla.isEmpty()) {
                doc.add(PdfReportRenderer.sinDatos());
                return;
            }
            doc.add(PdfReportRenderer.tabla(tabla));
        });
    }

    public byte[] lowStockToExcel(List<InventoryAlertDto> alertas) {
        ReportTable tabla = tablaInventario(alertas);
        return ExcelReportRenderer.render(List.of(new ExcelReportRenderer.Sheet("Inventario",
                "Alertas de inventario - CIMAxis", subtituloInventario(tabla), tabla, false)));
    }

    private static ReportTable tablaInventario(List<InventoryAlertDto> alertas) {
        List<InventoryAlertDto> lista = alertas != null ? alertas : List.of();
        return new ReportTable(List.of(ReportColumn.text("Producto", 3f),
                ReportColumn.of("Stock total", Kind.INTEGER, 1f),
                ReportColumn.of("Stock punto de venta", Kind.INTEGER, 1.2f),
                ReportColumn.of("Umbral de alerta", Kind.INTEGER, 1f)),
                lista.stream().map(a -> Arrays.<Object>asList(a.getProductName(), a.getTotalStock(),
                        a.getPointOfSaleStock(), a.getLowStockAlert())).toList());
    }

    private String subtituloInventario(ReportTable tabla) {
        int n = tabla.rows().size();
        return ReportFormatter.entero(n) + (n == 1 ? " producto" : " productos") + " en el umbral de alerta o por debajo · "
                + generado();
    }

    // ==================================================================== genericos (compatibilidad)

    /** Exportacion generica a Excel: los numeros se tratan como enteros. */
    public byte[] toExcel(String sheetName, List<String> headers, List<List<Object>> rows) {
        ReportTable tabla = generica(headers, rows);
        return ExcelReportRenderer.render(List.of(new ExcelReportRenderer.Sheet(sheetName, sheetName + " - CIMAxis",
                generado(), tabla, false)));
    }

    /** Exportacion generica a PDF con el marco de marca. */
    public byte[] toPdf(String title, List<String> headers, List<List<Object>> rows) {
        ReportTable tabla = generica(headers, rows);
        return PdfReportRenderer.render(title, generado(), logoBytes(), doc -> {
            if (tabla.isEmpty()) {
                doc.add(PdfReportRenderer.sinDatos());
                return;
            }
            doc.add(PdfReportRenderer.tabla(tabla));
        });
    }

    private static ReportTable generica(List<String> headers, List<List<Object>> rows) {
        List<List<Object>> filas = rows != null ? rows : List.of();
        List<ReportColumn> columnas = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            final int col = i;
            boolean numerica = !filas.isEmpty() && filas.stream()
                    .allMatch(f -> col >= f.size() || f.get(col) == null || f.get(col) instanceof Number);
            columnas.add(ReportColumn.of(headers.get(i), numerica ? Kind.INTEGER : Kind.TEXT, 1f));
        }
        return new ReportTable(columnas, filas);
    }

    // ==================================================================== utilidades

    private String generado() {
        return "Generado el " + ReportFormatter.fechaHora(LocalDateTime.now(clock.withZone(ReportFormatter.ZONA_CIMA)));
    }

    private String subtituloPeriodos(List<KpiSnapshotDto> historial) {
        String periodos = historial.isEmpty() ? "Sin períodos calculados"
                : (historial.size() == 1 ? "Período: " : "Períodos: ")
                        + ReportFormatter.rangoPeriodos(historial.stream().map(KpiSnapshotDto::getPeriod).toList());
        return periodos + " · " + generado();
    }

    /** Periodos sin repetir, del mas reciente al mas antiguo. */
    private static List<KpiSnapshotDto> ordenarPorPeriodo(List<KpiSnapshotDto> snapshots) {
        if (snapshots == null) return List.of();
        Map<String, KpiSnapshotDto> unicos = new LinkedHashMap<>();
        snapshots.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(KpiSnapshotDto::getPeriod, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(s -> unicos.putIfAbsent(String.valueOf(s.getPeriod()), s));
        return new ArrayList<>(unicos.values());
    }

    private static Color colorVariacion(String variacion) {
        if (variacion.startsWith("▲")) return ReportBranding.VERDE;
        if (variacion.startsWith("▼")) return ReportBranding.ROJO;
        return ReportBranding.GRIS_TEXTO;
    }

    private static PdfPCell sinBorde(PdfPTable tabla) {
        PdfPCell celda = new PdfPCell(tabla);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingRight(14);
        return celda;
    }

    private byte[] logoBytes() {
        return logo != null ? logo.bytes() : null;
    }
}
