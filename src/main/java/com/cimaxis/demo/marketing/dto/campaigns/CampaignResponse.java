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
    private String clientId;
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
