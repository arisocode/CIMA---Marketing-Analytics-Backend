package com.cimaxis.demo.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "KPI_SNAPSHOTS")
public class KpiSnapshot {

    /**
     * Entidad que representa un snapshot periódico de KPIs calculados
     * y almacenados en `KPI_SNAPSHOTS`.
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshots_id")
    private Integer snapshotsId;

    @Column(name = "period", length = 7, nullable = false)
    private String period;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "new_clients")
    private Integer newClients;

    @Column(name = "closed_projects")
    private Integer closedProjects;

    @Column(name = "estimated_revenue", precision = 14, scale = 2)
    private BigDecimal estimatedRevenue;

    @Column(name = "active_campaigns")
    private Integer activeCampaigns;

    @Column(name = "clients_contacted")
    private Integer clientsContacted;

    @Column(name = "response_rate", precision = 5, scale = 2)
    private BigDecimal responseRate;

    @Column(name = "avg_close_days", precision = 6, scale = 2)
    private BigDecimal avgCloseDays;

    @Column(name = "projects_in_progress")
    private Integer projectsInProgress;

    @Column(name = "calculated_by", length = 36)
    private String calculatedBy;
}
