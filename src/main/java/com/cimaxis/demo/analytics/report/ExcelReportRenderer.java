package com.cimaxis.demo.analytics.report;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Libros de Excel de CIMAxis con Apache POI. Cada hoja: fila 1 titulo, fila 2
 * fecha de generacion (y periodos), fila 3 encabezados en negrilla sobre el color
 * de marca, encabezado inmovilizado y autofiltro. Los numeros se guardan como
 * numeros con formato real para que el usuario pueda sumar:
 * moneda "$ #,##0", porcentaje "0.00 %" (se guarda 0,2667), enteros "0", dias "0.0".
 */
public final class ExcelReportRenderer {

    public static final String FORMATO_MONEDA = "$ #,##0";
    public static final String FORMATO_PORCENTAJE = "0.00 %";
    public static final String FORMATO_ENTERO = "0";
    public static final String FORMATO_DIAS = "0.0";
    /** [$-240A] = espanol de Colombia: el nombre del mes sale en espanol en cualquier Excel. */
    public static final String FORMATO_FECHA = "[$-240A]d \"de\" mmmm \"de\" yyyy";
    public static final String FORMATO_FECHA_HORA = "[$-240A]d \"de\" mmmm \"de\" yyyy, h:mm AM/PM";

    static final int FILA_TITULO = 0;
    static final int FILA_SUBTITULO = 1;
    static final int FILA_ENCABEZADO = 2;
    private static final int ANCHO_MINIMO = 10;
    private static final int ANCHO_MAXIMO = 60;
    private static final int ANCHO_MAXIMO_TEXTO_LARGO = 100;

    /** Hoja del libro. wrapLastColumn: la ultima columna es texto largo (se ajusta en varias lineas). */
    public record Sheet(String name, String title, String subtitle, ReportTable table, boolean wrapLastColumn) {
    }

    private ExcelReportRenderer() {
    }

    public static byte[] render(List<Sheet> hojas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            for (Sheet hoja : hojas) {
                escribir(libro, estilos, hoja);
            }
            libro.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando el archivo Excel: " + e.getMessage(), e);
        }
    }

    private static void escribir(XSSFWorkbook libro, Estilos estilos, Sheet hoja) {
        XSSFSheet sheet = libro.createSheet(nombreHoja(hoja.name()));
        List<ReportColumn> columnas = hoja.table().columns();
        int ultimaColumna = Math.max(0, columnas.size() - 1);

        Cell titulo = sheet.createRow(FILA_TITULO).createCell(0);
        titulo.setCellValue(hoja.title());
        titulo.setCellStyle(estilos.titulo);
        sheet.getRow(FILA_TITULO).setHeightInPoints(24);
        Cell subtitulo = sheet.createRow(FILA_SUBTITULO).createCell(0);
        subtitulo.setCellValue(hoja.subtitle() != null ? hoja.subtitle() : "");
        subtitulo.setCellStyle(estilos.subtitulo);
        if (ultimaColumna > 0) {
            sheet.addMergedRegion(new CellRangeAddress(FILA_TITULO, FILA_TITULO, 0, ultimaColumna));
            sheet.addMergedRegion(new CellRangeAddress(FILA_SUBTITULO, FILA_SUBTITULO, 0, ultimaColumna));
        }

        Row encabezado = sheet.createRow(FILA_ENCABEZADO);
        encabezado.setHeightInPoints(20);
        int[] anchos = new int[columnas.size()];
        for (int i = 0; i < columnas.size(); i++) {
            Cell c = encabezado.createCell(i);
            c.setCellValue(columnas.get(i).title());
            c.setCellStyle(columnas.get(i).kind().numeric() ? estilos.encabezadoNumero : estilos.encabezado);
            anchos[i] = columnas.get(i).title().length() + 3;
        }

        int filaActual = FILA_ENCABEZADO + 1;
        for (List<Object> datos : hoja.table().rows()) {
            Row row = sheet.createRow(filaActual++);
            for (int i = 0; i < columnas.size(); i++) {
                ReportColumn columna = columnas.get(i);
                Object valor = i < datos.size() ? datos.get(i) : null;
                Cell c = row.createCell(i);
                boolean largo = hoja.wrapLastColumn() && i == ultimaColumna;
                escribirValor(c, columna.kind(), valor, largo ? estilos.textoLargo : estilos.de(columna.kind()));
                anchos[i] = Math.max(anchos[i], ReportTable.formatear(columna.kind(), valor).length() + 2);
            }
        }

        for (int i = 0; i < columnas.size(); i++) {
            int maximo = hoja.wrapLastColumn() && i == ultimaColumna ? ANCHO_MAXIMO_TEXTO_LARGO : ANCHO_MAXIMO;
            sheet.setColumnWidth(i, Math.min(maximo, Math.max(ANCHO_MINIMO, anchos[i])) * 256);
        }
        // Impresion: horizontal, todas las columnas en el ancho de una hoja y encabezado repetido.
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setRepeatingRows(new CellRangeAddress(FILA_ENCABEZADO, FILA_ENCABEZADO, 0, ultimaColumna));
        sheet.createFreezePane(0, FILA_ENCABEZADO + 1);
        sheet.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, Math.max(FILA_ENCABEZADO, filaActual - 1),
                0, ultimaColumna));
    }

    private static void escribirValor(Cell c, ReportColumn.Kind kind, Object valor, XSSFCellStyle estilo) {
        c.setCellStyle(estilo);
        if (valor == null) {
            c.setCellValue("");
            return;
        }
        switch (kind) {
            case INTEGER, MONEY, DAYS -> c.setCellValue(ReportTable.decimal(valor).doubleValue());
            // Excel multiplica por 100 al mostrar: 26,67 % se guarda como 0,2667.
            case PERCENT -> c.setCellValue(ReportTable.decimal(valor)
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP).doubleValue());
            case DATE, DATETIME -> {
                Date fecha = aFecha(valor);
                if (fecha != null) c.setCellValue(fecha); else c.setCellValue(valor.toString());
            }
            case TEXT -> c.setCellValue(valor.toString());
        }
    }

    private static Date aFecha(Object valor) {
        ZoneId zona = ZoneId.systemDefault();
        if (valor instanceof LocalDateTime dt) return Date.from(dt.atZone(zona).toInstant());
        if (valor instanceof LocalDate d) return Date.from(d.atStartOfDay(zona).toInstant());
        return null;
    }

    /** Excel no admite : \ / ? * [ ] y limita el nombre a 31 caracteres. */
    static String nombreHoja(String nombre) {
        String limpio = nombre == null ? "Hoja" : nombre.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return limpio.length() > 31 ? limpio.substring(0, 31) : (limpio.isEmpty() ? "Hoja" : limpio);
    }

    /** Estilos compartidos por todas las hojas del libro (POI limita la cantidad de estilos). */
    private static final class Estilos {
        final XSSFCellStyle titulo;
        final XSSFCellStyle subtitulo;
        final XSSFCellStyle encabezado;
        final XSSFCellStyle encabezadoNumero;
        final XSSFCellStyle textoLargo;
        final Map<ReportColumn.Kind, XSSFCellStyle> porTipo = new EnumMap<>(ReportColumn.Kind.class);

        Estilos(XSSFWorkbook libro) {
            XSSFColor rojo = new XSSFColor(ReportBranding.rgb(ReportBranding.ROJO), null);
            XSSFColor tinta = new XSSFColor(ReportBranding.rgb(ReportBranding.TINTA), null);
            XSSFColor gris = new XSSFColor(ReportBranding.rgb(ReportBranding.GRIS_TEXTO), null);
            XSSFColor blanco = new XSSFColor(ReportBranding.rgb(ReportBranding.BLANCO), null);

            XSSFFont fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 15);
            fuenteTitulo.setColor(rojo);
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);
            titulo.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont fuenteSubtitulo = libro.createFont();
            fuenteSubtitulo.setItalic(true);
            fuenteSubtitulo.setFontHeightInPoints((short) 10);
            fuenteSubtitulo.setColor(gris);
            subtitulo = libro.createCellStyle();
            subtitulo.setFont(fuenteSubtitulo);

            XSSFFont fuenteEncabezado = libro.createFont();
            fuenteEncabezado.setBold(true);
            fuenteEncabezado.setColor(blanco);
            encabezado = libro.createCellStyle();
            encabezado.setFont(fuenteEncabezado);
            encabezado.setFillForegroundColor(rojo);
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            encabezado.setVerticalAlignment(VerticalAlignment.CENTER);
            encabezado.setBorderBottom(BorderStyle.THIN);
            encabezadoNumero = libro.createCellStyle();
            encabezadoNumero.cloneStyleFrom(encabezado);
            encabezadoNumero.setAlignment(HorizontalAlignment.RIGHT);

            XSSFFont fuenteDatos = libro.createFont();
            fuenteDatos.setColor(tinta);
            for (ReportColumn.Kind kind : ReportColumn.Kind.values()) {
                XSSFCellStyle estilo = libro.createCellStyle();
                estilo.setFont(fuenteDatos);
                estilo.setVerticalAlignment(VerticalAlignment.TOP);
                String formato = switch (kind) {
                    case MONEY -> FORMATO_MONEDA;
                    case PERCENT -> FORMATO_PORCENTAJE;
                    case INTEGER -> FORMATO_ENTERO;
                    case DAYS -> FORMATO_DIAS;
                    case DATE -> FORMATO_FECHA;
                    case DATETIME -> FORMATO_FECHA_HORA;
                    case TEXT -> null;
                };
                if (formato != null) estilo.setDataFormat(libro.createDataFormat().getFormat(formato));
                if (kind.numeric()) estilo.setAlignment(HorizontalAlignment.RIGHT);
                if (kind == ReportColumn.Kind.DATE || kind == ReportColumn.Kind.DATETIME) {
                    estilo.setAlignment(HorizontalAlignment.LEFT);
                }
                porTipo.put(kind, estilo);
            }
            textoLargo = libro.createCellStyle();
            textoLargo.cloneStyleFrom(porTipo.get(ReportColumn.Kind.TEXT));
            textoLargo.setWrapText(true);
        }

        XSSFCellStyle de(ReportColumn.Kind kind) {
            return porTipo.get(kind);
        }
    }
}
