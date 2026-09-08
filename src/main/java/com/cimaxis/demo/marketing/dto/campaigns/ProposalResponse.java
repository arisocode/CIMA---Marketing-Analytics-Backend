package com.cimaxis.demo.marketing.dto.campaigns;

import java.math.BigDecimal;
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
public class ProposalResponse {

    private Integer proposalId;
    private String clientId;
    private String description;
    private String documentUrl;
    private String status;
    private BigDecimal estimatedValue;
    private LocalDate createdDate;
    private LocalDate responseDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
