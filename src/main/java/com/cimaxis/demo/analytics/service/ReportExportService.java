package com.cimaxis.demo.analytics.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Exportacion de reportes en Excel y PDF.
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String[] KPI_HEADERS = {
            "Periodo", "Clientes nuevos", "Proyectos cerrados", "Ingresos estimados",
            "Campanas activas", "Clientes contactados", "Tasa de respuesta (%)",
            "Dias promedio de cierre", "Proyectos en proceso", "Calculado"
    };


    // Excel
    public byte[] kpisToExcel(List<KpiSnapshotDto> snapshots) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("KPIs CIMAxis");
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < KPI_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(KPI_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (KpiSnapshotDto s : snapshots) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nullSafe(s.getPeriod()));
                row.createCell(1).setCellValue(intValue(s.getNewClients()));
                row.createCell(2).setCellValue(intValue(s.getClosedProjects()));
                row.createCell(3).setCellValue(decimalValue(s.getEstimatedRevenue()));
                row.createCell(4).setCellValue(intValue(s.getActiveCampaigns()));
                row.createCell(5).setCellValue(intValue(s.getClientsContacted()));
                row.createCell(6).setCellValue(decimalValue(s.getResponseRate()));
                row.createCell(7).setCellValue(decimalValue(s.getAvgCloseDays()));
                row.createCell(8).setCellValue(intValue(s.getProjectsInProgress()));
                row.createCell(9).setCellValue(
                        s.getCalculatedAt() != null ? s.getCalculatedAt().format(STAMP) : "");
            }

            for (int i = 0; i < KPI_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Error generando el archivo Excel: " + e.getMessage(), e);
        }
    }

    /** Exportacion generica a Excel para cualquier reporte tabular. */
    public byte[] toExcel(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (List<Object> data : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < data.size(); i++) {
                    Object value = data.get(i);
                    if (value instanceof Number number) {
                        row.createCell(i).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(i).setCellValue(value != null ? value.toString() : "");
                    }
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Error generando el archivo Excel: " + e.getMessage(), e);
        }
    }

    // PDF
    public byte[] kpisToPdf(List<KpiSnapshotDto> snapshots) {
        List<List<Object>> rows = snapshots.stream()
                .map(s -> Arrays.<Object>asList(
                        nullSafe(s.getPeriod()),
                        intValue(s.getNewClients()),
                        intValue(s.getClosedProjects()),
                        decimalValue(s.getEstimatedRevenue()),
                        intValue(s.getActiveCampaigns()),
                        intValue(s.getClientsContacted()),
                        decimalValue(s.getResponseRate()),
                        decimalValue(s.getAvgCloseDays()),
                        intValue(s.getProjectsInProgress())))
                .toList();

        List<String> headers = List.of(KPI_HEADERS).subList(0, 9);
        return toPdf("Reporte de indicadores - CIMAxis", headers, rows);
    }

    public byte[] toPdf(String title, List<String> headers, List<List<Object>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph heading = new Paragraph(title);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);
            document.add(new Paragraph("Generado el " + LocalDateTime.now().format(STAMP)));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);

            for (String head : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(head));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (List<Object> data : rows) {
                for (Object value : data) {
                    table.addCell(new PdfPCell(new Paragraph(value != null ? value.toString() : "")));
                }
            }

            document.add(table);
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Error generando el archivo PDF: " + e.getMessage(), e);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private double intValue(Integer value) {
        return value != null ? value : 0;
    }

    private double decimalValue(BigDecimal value) {
        return value != null ? value.doubleValue() : 0d;
    }
}
