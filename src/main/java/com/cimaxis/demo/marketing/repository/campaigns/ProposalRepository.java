package com.cimaxis.demo.marketing.repository.campaigns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.campaigns.Proposal;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Integer> {

    List<Proposal> findByClientId(String clientId);

    List<Proposal> findByStatus(Proposal.ProposalStatus status);

    List<Proposal> findByClientIdAndStatus(String clientId, Proposal.ProposalStatus status);

    List<Proposal> findByResponseDateIsNullAndStatusInAndCreatedDateLessThanEqual(
            Collection<Proposal.ProposalStatus> statuses, LocalDate limitDate);

    @Query("SELECT COALESCE(SUM(p.estimatedValue), 0) FROM Proposal p "
            + "WHERE p.status = :status AND p.responseDate BETWEEN :from AND :to")
    BigDecimal sumValueByStatusBetween(@Param("status") Proposal.ProposalStatus status,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    long countByStatusAndResponseDateBetween(Proposal.ProposalStatus status, LocalDate from, LocalDate to);

    long countByCreatedDateBetween(LocalDate from, LocalDate to);
}
