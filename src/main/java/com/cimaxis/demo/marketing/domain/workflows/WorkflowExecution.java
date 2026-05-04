package com.cimaxis.demo.marketing.domain.workflows;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "WORKFLOW_EXECUTIONS")
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Integer executionId;

    @Column(name = "workflow_id", nullable = false)
    private Integer workflowId;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result")
    private ExecutionResult result;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "sent_message", columnDefinition = "TEXT")
    private String sentMessage;

    public enum ExecutionResult {
        success, failed, pending
    }
}
