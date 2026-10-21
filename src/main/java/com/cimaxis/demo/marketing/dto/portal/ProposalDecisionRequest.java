package com.cimaxis.demo.marketing.dto.portal;

/** decision: ACCEPTED o REJECTED. comment obligatorio si REJECTED (max. 1000). */
public record ProposalDecisionRequest(String decision, String comment) {
}
