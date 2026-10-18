package com.cimaxis.demo.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;

public record KpiSelection(
        YearMonth period,
        List<Client> newClients,
        List<Project> closedProjects,
        List<Proposal> approvedProposals,
        List<Campaign> activeCampaigns,
        List<ClientContact> contactedClients,
        List<ClientContact> respondedClients,
        List<ProjectCloseTime> closeTimes,
        List<Project> projectsInProgress) {

    /** Cliente con interacciones en el periodo: ultima fecha y cuantas hubo. */
    public record ClientContact(String clientId, LocalDateTime lastDate, long interactions) {
    }

    /** Proyecto cerrado con los dias entre el alta del cliente y el cierre. */
    public record ProjectCloseTime(Project project, Client client, long days) {
    }

    public BigDecimal estimatedRevenue() {
        return approvedProposals.stream()
                .map(Proposal::getEstimatedValue)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Clientes que respondieron sobre clientes contactados, en porcentaje con dos decimales. */
    public BigDecimal responseRate() {
        if (contactedClients.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(respondedClients.size())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(contactedClients.size()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal avgCloseDays() {
        if (closeTimes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long total = closeTimes.stream().mapToLong(ProjectCloseTime::days).sum();
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(closeTimes.size()), 2, RoundingMode.HALF_UP);
    }
}
