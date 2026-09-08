package com.cimaxis.demo.marketing.dto.workflows;

import java.util.List;

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
public class SchedulerRunResponse {

    private int executionsGenerated;
    private List<WorkflowExecutionResponse> executions;
}
