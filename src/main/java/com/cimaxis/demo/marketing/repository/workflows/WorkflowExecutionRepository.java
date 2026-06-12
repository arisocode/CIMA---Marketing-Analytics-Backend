package com.cimaxis.demo.marketing.repository.workflows;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;

import java.util.List;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Integer> {
    
    List<WorkflowExecution> findByWorkflowId(Integer workflowId);
    
    List<WorkflowExecution> findByClientId(String clientId);
    
    boolean existsByWorkflowIdAndClientId(Integer workflowId, String clientId);
}
