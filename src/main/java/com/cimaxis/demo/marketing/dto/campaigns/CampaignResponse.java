package com.cimaxis.demo.marketing.dto.campaigns;

import java.time.LocalDate;
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
public class CampaignResponse {

    private Integer campaignId;
    private String campaignName;
    private String campaignType;
    private String audienceType;
    /** null si la audiencia no es CLIENT. */
    private String clientId;
    private java.util.UUID segmentId;
    private String segmentName;
    /** Clientes que hoy forman la audiencia (se calcula en cada consulta). */
    private Integer audienceSize;
    private String projectId;
    private String createdBy;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String platforms;
    private String objective;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
