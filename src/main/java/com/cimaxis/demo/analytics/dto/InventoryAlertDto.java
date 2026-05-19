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
public class InventoryAlertDto {

    /**
     * DTO para alertas de inventario bajo. Incluye información del producto
     * y cantidades actuales para facilitar exportación y visualización.
     */

    private Integer inventoryId;
    private Integer productId;
    private String productName;
    private Integer totalStock;
    private Integer pointOfSaleStock;
    private Integer lowStockAlert;
    private String inventoryType;
}
