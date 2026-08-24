package com.cimaxis.demo.analytics.controller;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.analytics.dto.AnalyticsSummaryDto;
import com.cimaxis.demo.analytics.dto.CampaignStatusReportDto;
import com.cimaxis.demo.analytics.dto.ClientActivityDto;
import com.cimaxis.demo.analytics.dto.ClientPlanDistributionDto;
import com.cimaxis.demo.analytics.dto.InventoryAlertDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.service.AnalyticsService;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.analytics.service.ReportExportService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller REST del modulo Analytics.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final KpiCalculationService kpiCalculationService;
    private final ReportExportService reportExportService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @GetMapping("/customers/plan-distribution")
    public ResponseEntity<List<ClientPlanDistributionDto>> getCustomerPlanDistribution() {
        return ResponseEntity.ok(analyticsService.getClientPlanDistribution());
    }

    @GetMapping("/customers/activity")
    public ResponseEntity<List<ClientActivityDto>> getCustomerActivity() {
        return ResponseEntity.ok(analyticsService.getClientActivities());
    }

    @GetMapping("/campaigns/status")
    public ResponseEntity<List<CampaignStatusReportDto>> getCampaignStatusReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String clientId) {

        return ResponseEntity.ok(analyticsService.getCampaignStatusReport(from, to, clientId));
    }

    @GetMapping("/inventory/low-stock")
    public ResponseEntity<List<InventoryAlertDto>> getLowStockAlerts() {
        return ResponseEntity.ok(analyticsService.getLowStockAlerts());
    }

    /** Historial de snapshots almacenados. */
    @GetMapping("/kpis")
    public ResponseEntity<List<KpiSnapshotDto>> getKpiSnapshots() {
        return ResponseEntity.ok(analyticsService.getKpiSnapshots());
    }

    /**
     * KPIs calculados en vivo para el periodo indicado.
     */
    @GetMapping("/kpis/current")
    public ResponseEntity<KpiSnapshotDto> getCurrentKpis(
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(kpiCalculationService.calculate(period));
    }

    @GetMapping("/kpis/current/{period}")
    public ResponseEntity<KpiSnapshotDto> getCurrentKpisByPath(@PathVariable String period) {
        return ResponseEntity.ok(kpiCalculationService.calculate(period));
    }

    /** KPIs de un periodo especifico ya almacenado. */
    @GetMapping("/kpis/period/{period}")
    public ResponseEntity<KpiSnapshotDto> getKpisByPeriod(@PathVariable String period) {
        return ResponseEntity.ok(analyticsService.getKpiSnapshotByPeriod(period));
    }

    /** Serie historica entre dos periodos, para graficas de tendencia. */
    @GetMapping("/kpis/history")
    public ResponseEntity<List<KpiSnapshotDto>> getKpiHistory(@RequestParam String from,
                                                              @RequestParam String to) {
        return ResponseEntity.ok(analyticsService.getKpiHistory(from, to));
    }

    /** Recalcula y almacena el snapshot del periodo. */
    @PostMapping("/kpis/calculate")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<KpiSnapshotDto> calculateKpis(@RequestParam(required = false) String period,
                                                        HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ResponseEntity.ok(kpiCalculationService.calculateAndStore(period, userId));
    }

    @PostMapping("/kpis/calculate/{period}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<KpiSnapshotDto> calculateKpisByPath(@PathVariable String period,
                                                              HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ResponseEntity.ok(kpiCalculationService.calculateAndStore(period, userId));
    }

    @GetMapping("/export/kpis")
    public ResponseEntity<byte[]> exportKpis(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String period) {

        List<KpiSnapshotDto> data = period != null
                ? List.of(kpiCalculationService.calculate(period))
                : analyticsService.getKpiSnapshots();

        if ("pdf".equalsIgnoreCase(format)) {
            return descarga(reportExportService.kpisToPdf(data),
                    "kpis-cimaxis.pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.kpisToExcel(data),
                "kpis-cimaxis.xlsx",
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/campaigns")
    public ResponseEntity<byte[]> exportCampaigns(@RequestParam(defaultValue = "xlsx") String format) {

        List<String> headers = List.of("Estado", "Cantidad de campanas");
        List<List<Object>> rows = analyticsService.getCampaignStatusReport(null, null, null).stream()
                .map(dto -> List.<Object>of(dto.getStatus(), dto.getCampaignCount()))
                .toList();

        if ("pdf".equalsIgnoreCase(format)) {
            return descarga(reportExportService.toPdf("Campanas por estado - CIMAxis", headers, rows),
                    "campanas-cimaxis.pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.toExcel("Campanas", headers, rows),
                "campanas-cimaxis.xlsx",
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/low-stock")
    public ResponseEntity<byte[]> exportLowStock(@RequestParam(defaultValue = "xlsx") String format) {

        List<String> headers = List.of("Producto", "Stock total", "Stock punto de venta", "Umbral de alerta");
        List<List<Object>> rows = analyticsService.getLowStockAlerts().stream()
                .map(dto -> java.util.Arrays.<Object>asList(
                        dto.getProductName(),
                        dto.getTotalStock(),
                        dto.getPointOfSaleStock(),
                        dto.getLowStockAlert()))
                .toList();

        if ("pdf".equalsIgnoreCase(format)) {
            return descarga(reportExportService.toPdf("Alertas de inventario - CIMAxis", headers, rows),
                    "inventario-cimaxis.pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.toExcel("Inventario", headers, rows),
                "inventario-cimaxis.xlsx",
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private ResponseEntity<byte[]> descarga(byte[] contenido, String nombre, MediaType tipo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(tipo)
                .body(contenido);
    }
}
