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
public class ClientActivityDto {

    /**
     * DTO que resume la actividad de un cliente:
     * - `clientId`: id del cliente en la tabla CLIENTS
     * - `plan`: plan asignado al cliente
     * - `campaignCount`: número de campañas asociadas
     * - `projectCount`: número de proyectos asociados
     */

    private String clientId;
    private String plan;
    private long campaignCount;
    private long projectCount;
}
