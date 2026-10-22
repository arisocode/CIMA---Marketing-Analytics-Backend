package com.cimaxis.demo.marketing.dto.commercial;

import java.math.BigDecimal;
import java.util.List;

import com.cimaxis.demo.marketing.dto.whatsapp.WhatsappPendingItem;

/** Panel Hoy. Cada lista trae maximo 20 elementos; counts trae el total real. */
public record TodayResponse(List<ClientWithoutContact> clientsWithoutContact,
                            List<ProposalWithoutResponse> proposalsWithoutResponse,
                            List<ApprovedWithoutProject> approvedWithoutProject,
                            List<WhatsappPendingItem> whatsappPending,
                            Counts counts) {

    /** daysSinceContact null = nunca fue contactado. */
    public record ClientWithoutContact(String clientId, String name, String plan, Long daysSinceContact) {
    }

    public record ProposalWithoutResponse(Integer proposalId, String title, String clientId,
                                          String clientName, long daysOpen, BigDecimal value) {
    }

    public record ApprovedWithoutProject(Integer proposalId, String title, String clientId,
                                         String clientName, BigDecimal value) {
    }

    public record Counts(long clientsWithoutContact, long proposalsWithoutResponse, long approvedWithoutProject,
                         long whatsappPending) {
    }
}
