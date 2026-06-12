package com.cimaxis.demo.analytics.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.analytics.dto.AnalyticsSummaryDto;
import com.cimaxis.demo.analytics.dto.CampaignStatusReportDto;
import com.cimaxis.demo.analytics.dto.ClientActivityDto;
import com.cimaxis.demo.analytics.dto.ClientPlanDistributionDto;
import com.cimaxis.demo.analytics.dto.InventoryAlertDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.service.AnalyticsService;

@RestController
@RequestMapping("/api/v1/analytics")
/**
 * Controller REST que expone los endpoints del módulo Analytics.
 *
 * Responsable únicamente de convertir llamadas HTTP a llamadas al
 * servicio `AnalyticsService` y devolver DTOs serializables.
 */
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary() {
        // Resumen agregado del sistema
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @GetMapping("/customers/plan-distribution")
    public ResponseEntity<List<ClientPlanDistributionDto>> getCustomerPlanDistribution() {
        // Distribución de clientes por plan
        return ResponseEntity.ok(analyticsService.getClientPlanDistribution());
    }

    @GetMapping("/customers/activity")
    public ResponseEntity<List<ClientActivityDto>> getCustomerActivity() {
        // Actividad resumida por cliente
        return ResponseEntity.ok(analyticsService.getClientActivities());
    }

    @GetMapping("/campaigns/status")
    public ResponseEntity<List<CampaignStatusReportDto>> getCampaignStatusReport() {
        // Conteo de campañas por estado
        return ResponseEntity.ok(analyticsService.getCampaignStatusReport());
    }

    @GetMapping("/inventory/low-stock")
    public ResponseEntity<List<InventoryAlertDto>> getLowStockAlerts() {
        // Alertas de inventario con stock bajo
        return ResponseEntity.ok(analyticsService.getLowStockAlerts());
    }

    @GetMapping("/kpis")
    public ResponseEntity<List<KpiSnapshotDto>> getKpiSnapshots() {
        // Historial de KPI snapshots
        return ResponseEntity.ok(analyticsService.getKpiSnapshots());
    }
}
