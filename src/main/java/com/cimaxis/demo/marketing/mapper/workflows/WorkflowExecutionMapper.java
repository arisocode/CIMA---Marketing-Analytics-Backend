package com.cimaxis.demo.marketing.mapper.workflows;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutionResponse;

@Component
public class WorkflowExecutionMapper {

    public WorkflowExecutionResponse toResponse(WorkflowExecution execution) {
        if (execution == null) {
            return null;
        }
        return WorkflowExecutionResponse.builder()
                .executionId(execution.getExecutionId())
                .workflowId(execution.getWorkflowId())
                .clientId(execution.getClientId())
                .executedAt(execution.getExecutedAt())
                .result(execution.getResult() != null ? execution.getResult().name() : null)
                .errorDetail(execution.getErrorDetail())
                .sentMessage(execution.getSentMessage())
                .build();
    }

    public List<WorkflowExecutionResponse> toResponseList(List<WorkflowExecution> executions) {
        if (executions == null || executions.isEmpty()) {
            return Collections.emptyList();
        }
        return executions.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
