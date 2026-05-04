package com.cimaxis.demo.marketing.repository.interactions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;

import java.util.List;

@Repository
public interface MarketingInteractionRepository extends JpaRepository<MarketingInteraction, Integer> {
    
    List<MarketingInteraction> findByCampaignId(Integer campaignId);
    
    List<MarketingInteraction> findByClientId(Integer clientId);
    
    List<MarketingInteraction> findByExecutionId(Integer executionId);
}
