package com.cimaxis.demo.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
public class KpiSnapshotDto {

    /**
     * DTO que representa un snapshot de KPIs calculado periódicamente.
     * Corresponde a los registros de la tabla `KPI_SNAPSHOTS`.
     */

    private Integer snapshotsId;
    private String period;
    private LocalDateTime calculatedAt;
    private Integer newClients;
    private Integer closedProjects;
    private BigDecimal estimatedRevenue;
    private Integer activeCampaigns;
    private Integer clientsContacted;
    private BigDecimal responseRate;
    private BigDecimal avgCloseDays;
    private Integer projectsInProgress;
    private String calculatedBy;
}
