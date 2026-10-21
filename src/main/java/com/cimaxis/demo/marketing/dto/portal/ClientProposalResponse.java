package com.cimaxis.demo.marketing.dto.portal;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Propuesta tal como la ve el cliente en el portal. */
public record ClientProposalResponse(Integer proposalId, String title, String description, BigDecimal value,
                                     String status, String documentUrl, LocalDate createdDate,
                                     LocalDate responseDate, String clientComment, String projectId) {
}
