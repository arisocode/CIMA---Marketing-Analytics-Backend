package com.cimaxis.demo.marketing.domain.plans;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plan comercial configurable (Platinum, Oro, Diamante y los que CIMA cree).
 *
 * Los clientes guardan el NOMBRE del plan en CLIENTS.plan; por eso al renombrar
 * un plan se actualizan tambien sus clientes. Un plan no se borra: se desactiva.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "marketing_plans")
public class MarketingPlan {

    @Id
    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    /** Color en hexadecimal (#RRGGBB) para chips y graficas. */
    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
