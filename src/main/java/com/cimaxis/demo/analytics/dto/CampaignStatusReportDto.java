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
public class CampaignStatusReportDto {

    /**
     * DTO para reportes de campañas agrupadas por estado.
     * - `status`: nombre del estado (p.ej. Active, Draft)
     * - `campaignCount`: cantidad de campañas con ese estado
     */

    private String status;
    private long campaignCount;
}
