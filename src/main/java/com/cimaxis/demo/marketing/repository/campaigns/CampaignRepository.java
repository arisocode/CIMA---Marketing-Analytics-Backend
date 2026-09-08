package com.cimaxis.demo.marketing.repository.campaigns;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.campaigns.Campaign;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Integer> {

    List<Campaign> findByClientId(String clientId);

    List<Campaign> findByStatus(Campaign.CampaignStatus status);

    List<Campaign> findByCreatedBy(String createdBy);

    List<Campaign> findByStartDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT COUNT(c) FROM Campaign c "
            + "WHERE c.status = :status "
            + "AND c.startDate <= :to "
            + "AND (c.endDate IS NULL OR c.endDate >= :from)")
    long countActiveInPeriod(@Param("status") Campaign.CampaignStatus status,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to);
}
