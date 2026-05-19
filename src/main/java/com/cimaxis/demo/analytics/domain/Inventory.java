package com.cimaxis.demo.analytics.domain;

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
@Table(name = "INVENTORY")
public class Inventory {

    /**
     * Entidad que mapea la tabla `INVENTORY`. Contiene información de stock
     * y umbrales para alertas.
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Integer inventoryId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "inventory_type", length = 50)
    private String inventoryType;

    @Column(name = "total_stock")
    private Integer totalStock;

    @Column(name = "point_of_sale_stock")
    private Integer pointOfSaleStock;

    @Column(name = "low_stock_alert")
    private Integer lowStockAlert;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
