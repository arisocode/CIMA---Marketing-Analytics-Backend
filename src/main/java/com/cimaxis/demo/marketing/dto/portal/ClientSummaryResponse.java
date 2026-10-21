package com.cimaxis.demo.marketing.dto.portal;

public record ClientSummaryResponse(String clientName, String plan, String planColor, long pendingProposals,
                                    long unreadMessages, long approvedProposals) {
}
