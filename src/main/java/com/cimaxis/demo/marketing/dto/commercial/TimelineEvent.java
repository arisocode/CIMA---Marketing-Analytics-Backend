package com.cimaxis.demo.marketing.dto.commercial;

import java.time.LocalDateTime;

/** type: PLAN, CAMPAIGN, MESSAGE, RESPONSE, PROPOSAL, PROPOSAL_DECISION o PROJECT. */
public record TimelineEvent(String type, LocalDateTime date, String title, String detail, String refId) {
}
