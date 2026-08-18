package com.cimaxis.demo.marketing.dto.interactions;

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
public class MarketingInteractionResponse {

    private Integer interactionId;
    private Integer campaignId;
    private String clientId;
    private Integer executionId;
    private String loggedBy;
    private LocalDateTime contactDate;
    private String interactionType;
    private String channel;
    private String response;
}
