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
public class ClientPlanDistributionDto {

    /**
     * DTO simple que representa la distribución de clientes por plan.
     * - `plan`: nombre del plan
     * - `clientCount`: cantidad de clientes en ese plan
     * - `color`: color del plan (null si el plan no esta en el catalogo)
     */

    private String plan;
    private long clientCount;
    private String color;
}
