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
import com.cimaxis.demo.analytics.dto.KpiComparisonDto;
import com.cimaxis.demo.analytics.dto.KpiDetailDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.dto.ProjectAnalyticsDto;
import com.cimaxis.demo.analytics.service.AnalyticsService;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.analytics.service.KpiDetailService;
import com.cimaxis.demo.analytics.report.CampaignReportRow;
import com.cimaxis.demo.analytics.report.CampaignReportService;
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
    private final KpiDetailService kpiDetailService;
    private final ReportExportService reportExportService;
    private final CampaignReportService campaignReportService;

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

    /** Registros que componen un KPI del periodo (los mismos que dan su numero). */
    @GetMapping("/kpis/{kpi}/detail")
    public ResponseEntity<KpiDetailDto> getKpiDetail(@PathVariable String kpi,
                                                     @RequestParam(required = false) String period) {
        return ResponseEntity.ok(kpiDetailService.getDetail(kpi, period));
    }

    /** Los ocho KPIs del periodo frente al mes anterior. */
    @GetMapping("/kpis/compare")
    public ResponseEntity<KpiComparisonDto> compareKpis(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(kpiDetailService.compare(period));
    }

    /** Proyectos de la proyeccion local con su cliente, filtrables por estado y cliente. */
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectAnalyticsDto>> getProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientId) {
        return ResponseEntity.ok(kpiDetailService.getProjects(status, clientId));
    }

    /** Exporta el detalle de un KPI en Excel o PDF. */
    @GetMapping("/export/kpis/{kpi}/detail")
    public ResponseEntity<byte[]> exportKpiDetail(@PathVariable String kpi,
                                                  @RequestParam(required = false) String period,
                                                  @RequestParam(defaultValue = "xlsx") String format) {
        KpiDetailDto detalle = kpiDetailService.getDetail(kpi, period);

        String archivo = "kpi-" + detalle.kpi() + "-" + detalle.period();
        if ("pdf".equalsIgnoreCase(format)) {
            return descarga(reportExportService.kpiDetailToPdf(detalle),
                    archivo + ".pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.kpiDetailToExcel(detalle),
                archivo + ".xlsx",
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/kpis")
    public ResponseEntity<byte[]> exportKpis(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String period) {

        List<KpiSnapshotDto> data = period != null
                ? List.of(kpiCalculationService.calculate(period))
                : analyticsService.getKpiSnapshots();

        if ("pdf".equalsIgnoreCase(format)) {
            // Con un solo periodo, las variaciones se comparan con el mes anterior.
            KpiSnapshotDto anterior = period != null
                    ? kpiCalculationService.calculate(kpiCalculationService.resolvePeriod(period).minusMonths(1).toString())
                    : null;
            return descarga(reportExportService.kpisToPdf(data, anterior),
                    "kpis-cimaxis.pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.kpisToExcel(data),
                "kpis-cimaxis.xlsx",
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/campaigns")
    public ResponseEntity<byte[]> exportCampaigns(@RequestParam(defaultValue = "xlsx") String format) {

        List<CampaignReportRow> campanas = campaignReportService.rows();

        if ("pdf".equalsIgnoreCase(format)) {
            return descarga(reportExportService.campaignsToPdf(campanas),
                    "campanas-cimaxis.pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.campaignsToExcel(campanas),
                "campanas-cimaxis.xlsx",
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/low-stock")
    public ResponseEntity<byte[]> exportLowStock(@RequestParam(defaultValue = "xlsx") String format) {

        List<InventoryAlertDto> alertas = analyticsService.getLowStockAlerts();

        if ("pdf".equalsIgnoreCase(format)) {
            return descarga(reportExportService.lowStockToPdf(alertas),
                    "inventario-cimaxis.pdf", MediaType.APPLICATION_PDF);
        }
        return descarga(reportExportService.lowStockToExcel(alertas),
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
