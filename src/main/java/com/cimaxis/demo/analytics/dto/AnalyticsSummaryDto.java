package com.cimaxis.demo.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryDto {

    /**
     * DTO que representa un resumen consolidado de métricas del sistema.
     * Usado por el endpoint `GET /api/analytics/summary`.
     */

    private long totalClients;
    private long totalUsers;
    private long totalCampaigns;
    private long activeCampaigns;
    private long totalProjects;
    private long projectsInProgress;
    private long totalProducts;
    private long totalInventoryItems;
    private long totalStock;
    private long lowStockAlerts;
    private long totalKpiSnapshots;
    private long totalMarketingInteractions;
}
