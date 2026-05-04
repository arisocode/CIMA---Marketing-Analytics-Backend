package com.cimaxis.demo.marketing.repository.campaigns;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.campaigns.Campaign;

import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Integer> {
    
    List<Campaign> findByClientId(Integer clientId);
    
    List<Campaign> findByStatus(Campaign.CampaignStatus status);
    
    List<Campaign> findByCreatedBy(Integer createdBy);
}
