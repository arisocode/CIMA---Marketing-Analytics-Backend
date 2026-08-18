package com.cimaxis.demo.marketing.repository.interactions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarketingInteractionRepository extends JpaRepository<MarketingInteraction, Integer> {

    List<MarketingInteraction> findByCampaignId(Integer campaignId);

    List<MarketingInteraction> findByClientId(String clientId);

    List<MarketingInteraction> findByExecutionId(Integer executionId);

    /** Ultima interaccion registrada con un cliente */
    MarketingInteraction findTopByClientIdOrderByContactDateDesc(String clientId);

    List<MarketingInteraction> findByContactDateBetween(LocalDateTime from, LocalDateTime to);

    List<MarketingInteraction> findByCampaignIdAndContactDateBetween(
            Integer campaignId, LocalDateTime from, LocalDateTime to);

    long countByContactDateBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT i.clientId) FROM MarketingInteraction i "
            + "WHERE i.contactDate BETWEEN :from AND :to")
    long countDistinctClientsContactedBetween(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(i) FROM MarketingInteraction i "
            + "WHERE i.contactDate BETWEEN :from AND :to "
            + "AND (i.response IS NOT NULL OR i.interactionType IN :respondedTypes)")
    long countResponsesBetween(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("respondedTypes") List<MarketingInteraction.InteractionType> respondedTypes);
}
