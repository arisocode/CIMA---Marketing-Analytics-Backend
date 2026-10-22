package com.cimaxis.demo.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cimaxis.demo.analytics.dto.KpiDetailDto;
import com.cimaxis.demo.analytics.dto.KpiDetailItemDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.report.CampaignReportRow;
import com.cimaxis.demo.analytics.report.ExcelReportRenderer;
import com.cimaxis.demo.analytics.report.ReportLogo;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/**
 * Reportes descargables (PDF y Excel). Ahora = 22 de octubre de 2026, 4:36 p. m. en Bogotá.
 * Los archivos de ejemplo quedan en target/reportes-ejemplo/ para revisarlos a mano.
 */
class ReportExportServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-22T21:36:00Z"), ZoneOffset.UTC);
    private static final Path CARPETA_EJEMPLOS = Paths.get("target", "reportes-ejemplo");

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(new ReportLogo(""), RELOJ);
    }

    // ================================================================== indicadores

    @Test
    @DisplayName("PDF de indicadores con datos: formatos en español, resumen, historial, definiciones y pie")
    void pdfDeIndicadores() throws Exception {
        byte[] pdf = service.kpisToPdf(historial());
        guardar("indicadores.pdf", pdf);

        String texto = textoPdf(pdf);
        assertThat(texto).contains("Reporte de indicadores");
        assertThat(texto).contains("Períodos: Agosto 2026 a Octubre 2026");
        assertThat(texto).contains("Generado el 22 de octubre de 2026, 4:36 p. m.");
        assertThat(texto).contains("Resumen del último período");
        assertThat(texto).contains("$ 4.500.000");
        assertThat(texto).contains("26,67 %");
        assertThat(texto).contains("19,0 días");
        assertThat(texto).contains("+12,5 %");
        assertThat(texto).contains("Historial por período");
        assertThat(texto).contains("Octubre 2026");
        assertThat(texto).contains("Cómo leer estos indicadores");
        assertThat(texto).contains("Campañas activas");
        assertThat(texto).contains("Página 1 de");
        assertThat(texto).doesNotContain("4500000.0");
    }

    @Test
    @DisplayName("PDF de indicadores sin datos: una página con encabezado y el mensaje, nunca vacío")
    void pdfDeIndicadoresSinDatos() throws Exception {
        byte[] pdf = service.kpisToPdf(List.of());
        guardar("indicadores-sin-datos.pdf", pdf);

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String texto = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(texto).contains("Reporte de indicadores");
        assertThat(texto).contains("No hay datos para el período seleccionado");
        assertThat(texto).contains("Página 1 de");
    }

    @Test
    @DisplayName("Con un solo período, la variación usa el período anterior recibido")
    void variacionConPeriodoAnteriorExterno() throws Exception {
        KpiSnapshotDto anterior = snapshot("2026-09", 8, "4000000", "20.00", "20.0");

        KpiSnapshotDto octubre = snapshot("2026-10", 9, "4500000", "26.67", "19.0");
        String texto = textoPdf(service.kpisToPdf(List.of(octubre), anterior));

        assertThat(texto).contains("Período: Octubre 2026");
        assertThat(texto).contains("frente a Septiembre 2026");
        assertThat(texto).contains("+12,5 %");
    }

    @Test
    @DisplayName("Excel de indicadores: números reales con formato de moneda, porcentaje, entero y días")
    void excelDeIndicadores() throws Exception {
        byte[] xlsx = service.kpisToExcel(historial());
        guardar("indicadores.xlsx", xlsx);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheet("Indicadores");
            assertThat(hoja).isNotNull();
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Reporte de indicadores - CIMAxis");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue())
                    .contains("Generado el 22 de octubre de 2026, 4:36 p. m.");

            Row encabezado = hoja.getRow(2);
            assertThat(encabezado.getCell(0).getStringCellValue()).isEqualTo("Período");
            assertThat(encabezado.getCell(3).getStringCellValue()).isEqualTo("Ingresos estimados");
            assertThat(encabezado.getCell(4).getStringCellValue()).isEqualTo("Campañas activas");
            assertThat(encabezado.getCell(0).getCellStyle().getFillForegroundColorColor()).isNotNull();

            Row octubre = hoja.getRow(3);
            assertThat(octubre.getCell(0).getStringCellValue()).isEqualTo("Octubre 2026");
            Cell ingresos = octubre.getCell(3);
            assertThat(ingresos.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(ingresos.getNumericCellValue()).isEqualTo(4500000.0);
            assertThat(ingresos.getCellStyle().getDataFormatString()).isEqualTo(ExcelReportRenderer.FORMATO_MONEDA);
            Cell clientes = octubre.getCell(1);
            assertThat(clientes.getNumericCellValue()).isEqualTo(9.0);
            assertThat(clientes.getCellStyle().getDataFormatString()).isEqualTo("0");
            Cell tasa = octubre.getCell(6);
            assertThat(tasa.getNumericCellValue()).isEqualTo(0.2667);
            assertThat(tasa.getCellStyle().getDataFormatString()).isEqualTo("0.00 %");
            Cell dias = octubre.getCell(7);
            assertThat(dias.getNumericCellValue()).isEqualTo(19.0);
            assertThat(dias.getCellStyle().getDataFormatString()).isEqualTo("0.0");

            assertThat(hoja.getPaneInformation().isFreezePane()).isTrue();
            assertThat(hoja.getRow(3)).isNotNull();

            Sheet definiciones = libro.getSheet("Definiciones");
            assertThat(definiciones).isNotNull();
            assertThat(definiciones.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Definición");
            assertThat(definiciones.getLastRowNum()).isEqualTo(2 + 8);
            assertThat(definiciones.getRow(5).getCell(1).getStringCellValue())
                    .contains("propuestas aprobadas");
        }
    }

    // ================================================================== campañas

    @Test
    @DisplayName("PDF de campañas: nombre del cliente, valores traducidos y resumen por estado y tipo")
    void pdfDeCampanas() throws Exception {
        byte[] pdf = service.campaignsToPdf(campanas());
        guardar("campanas.pdf", pdf);

        String texto = textoPdf(pdf);
        assertThat(texto).contains("Reporte de campañas");
        assertThat(texto).contains("Ana Pérez");
        assertThat(texto).contains("Venta directa");
        assertThat(texto).contains("Posicionamiento");
        assertThat(texto).contains("Activa");
        assertThat(texto).contains("Pausada");
        assertThat(texto).contains("1 de octubre de 2026");
        assertThat(texto).contains("Página 1 de");
        assertThat(texto).doesNotContain("Direct_sales");
    }

    @Test
    @DisplayName("Excel de campañas: fechas reales con formato en español y hoja de resumen")
    void excelDeCampanas() throws Exception {
        byte[] xlsx = service.campaignsToExcel(campanas());
        guardar("campanas.xlsx", xlsx);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheet("Campañas");
            Row primera = hoja.getRow(3);
            assertThat(primera.getCell(1).getStringCellValue()).isEqualTo("Ana Pérez");
            assertThat(primera.getCell(2).getStringCellValue()).isEqualTo("Venta directa");
            assertThat(primera.getCell(3).getStringCellValue()).isEqualTo("Activa");
            assertThat(primera.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(primera.getCell(4).getCellStyle().getDataFormatString())
                    .isEqualTo(ExcelReportRenderer.FORMATO_FECHA);
            assertThat(libro.getSheet("Resumen")).isNotNull();
        }
    }

    @Test
    @DisplayName("PDF de campañas sin datos")
    void pdfDeCampanasSinDatos() throws Exception {
        assertThat(textoPdf(service.campaignsToPdf(List.of())))
                .contains("No hay datos para el período seleccionado");
    }

    // ================================================================== detalle de un indicador

    @Test
    @DisplayName("PDF del detalle de ingresos: total destacado y registros con formato de moneda")
    void pdfDeDetalle() throws Exception {
        byte[] pdf = service.kpiDetailToPdf(detalleIngresos());
        guardar("detalle-ingresos.pdf", pdf);

        String texto = textoPdf(pdf);
        assertThat(texto).contains("Detalle: Ingresos estimados");
        assertThat(texto).contains("Período: Octubre 2026");
        assertThat(texto).contains("$ 4.500.000");
        assertThat(texto).contains("$ 3.000.000");
        assertThat(texto).contains("Aprobada");
        assertThat(texto).contains("2 registros");
    }

    @Test
    @DisplayName("Excel del detalle de ingresos: valor numérico con formato de moneda")
    void excelDeDetalle() throws Exception {
        byte[] xlsx = service.kpiDetailToExcel(detalleIngresos());
        guardar("detalle-ingresos.xlsx", xlsx);

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row fila = libro.getSheet("Detalle").getRow(3);
            assertThat(fila.getCell(3).getNumericCellValue()).isEqualTo(3000000.0);
            assertThat(fila.getCell(3).getCellStyle().getDataFormatString())
                    .isEqualTo(ExcelReportRenderer.FORMATO_MONEDA);
            assertThat(fila.getCell(4).getStringCellValue()).isEqualTo("Aprobada");
        }
    }

    @Test
    @DisplayName("Detalle sin registros: total en cero y mensaje de vacío")
    void detalleVacio() throws Exception {
        String texto = textoPdf(service.kpiDetailToPdf(
                new KpiDetailDto("revenue", "2026-10", 0, BigDecimal.ZERO, List.of())));
        assertThat(texto).contains("$ 0");
        assertThat(texto).contains("No hay datos para el período seleccionado");
    }

    // ================================================================== utilidades

    private static List<KpiSnapshotDto> historial() {
        return List.of(
                snapshot("2026-08", 6, "2500000", "30.00", "25.5"),
                snapshot("2026-10", 9, "4500000", "26.67", "19.0"),
                snapshot("2026-09", 8, "4000000", "20.00", "20.0"));
    }

    private static KpiSnapshotDto snapshot(String periodo, int clientesNuevos, String ingresos, String tasa,
                                           String dias) {
        return KpiSnapshotDto.builder().period(periodo).newClients(clientesNuevos).closedProjects(2)
                .estimatedRevenue(new BigDecimal(ingresos)).activeCampaigns(3).clientsContacted(15)
                .responseRate(new BigDecimal(tasa)).avgCloseDays(new BigDecimal(dias)).projectsInProgress(4)
                .calculatedAt(LocalDateTime.of(2026, 10, 22, 1, 30)).build();
    }

    private static List<CampaignReportRow> campanas() {
        return List.of(
                new CampaignReportRow("Lanzamiento Oro", "Ana Pérez", "Direct_sales", "Active",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "Instagram, Facebook",
                        "Aumentar las ventas del plan Oro en un 20 % durante el último trimestre del año con pauta y "
                                + "contenido de valor"),
                new CampaignReportRow("Marca 2027", "Luis Gómez", "Positioning", "Paused",
                        LocalDate.of(2026, 9, 15), null, "LinkedIn", "Posicionar la marca"));
    }

    private static KpiDetailDto detalleIngresos() {
        return new KpiDetailDto("revenue", "2026-10", 2, new BigDecimal("4500000"), List.of(
                new KpiDetailItemDto("7", "Branding 2027", "Ana Pérez", LocalDateTime.of(2026, 10, 14, 0, 0),
                        new BigDecimal("3000000"), "Approved", "c-1", "Ana Pérez"),
                new KpiDetailItemDto("9", "Pauta digital", "Luis Gómez", LocalDateTime.of(2026, 10, 20, 0, 0),
                        new BigDecimal("1500000"), "Approved", "c-2", "Luis Gómez")));
    }

    private static String textoPdf(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        return texto.toString();
    }

    private static void guardar(String nombre, byte[] contenido) throws IOException {
        Files.createDirectories(CARPETA_EJEMPLOS);
        Files.write(CARPETA_EJEMPLOS.resolve(nombre), contenido);
    }
}
