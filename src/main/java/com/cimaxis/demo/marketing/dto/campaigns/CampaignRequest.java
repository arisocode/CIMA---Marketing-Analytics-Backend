package com.cimaxis.demo.marketing.dto.campaigns;

import java.time.LocalDate;

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
public class CampaignRequest {

    private String campaignName;
    private String campaignType;
    /** CLIENT (por defecto), SEGMENT o ALL. */
    private String audienceType;
    /** Obligatorio solo con audienceType CLIENT. */
    private String clientId;
    /** Segmento guardado; obligatorio solo con audienceType SEGMENT. */
    private java.util.UUID segmentId;
    private String projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String platforms;
    private String objective;
}
