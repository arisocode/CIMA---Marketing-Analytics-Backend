package com.cimaxis.demo.marketing.dto.workflows;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowExecutionResponse {

    private Integer executionId;
    private Integer workflowId;
    private String clientId;
    private LocalDateTime executedAt;
    private String result;
    private String errorDetail;
    private String sentMessage;
}
