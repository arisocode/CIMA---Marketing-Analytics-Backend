package com.cimaxis.demo.marketing.repository.workflows;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Integer> {

    List<WorkflowExecution> findByWorkflowId(Integer workflowId);

    List<WorkflowExecution> findByClientId(String clientId);

    boolean existsByWorkflowIdAndClientId(Integer workflowId, String clientId);

    List<WorkflowExecution> findByExecutedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByResultAndExecutedAtBetween(WorkflowExecution.ExecutionResult result,
                                           LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT e.clientId) FROM WorkflowExecution e "
            + "WHERE e.executedAt BETWEEN :from AND :to AND e.result = :result")
    long countDistinctClientsReached(@Param("result") WorkflowExecution.ExecutionResult result,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
