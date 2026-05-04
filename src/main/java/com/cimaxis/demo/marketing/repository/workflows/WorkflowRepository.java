package com.cimaxis.demo.marketing.repository.workflows;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.workflows.Workflow;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Integer> {
    
    List<Workflow> findByCampaignId(Integer campaignId);
    
    List<Workflow> findByActiveTrue();
    
    List<Workflow> findByTriggerType(Workflow.TriggerType triggerType);
}