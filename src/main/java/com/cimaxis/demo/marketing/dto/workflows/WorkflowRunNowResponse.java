package com.cimaxis.demo.marketing.dto.workflows;

import java.util.List;

public record WorkflowRunNowResponse(List<WorkflowExecutedClient> executed, List<WorkflowSkippedClient> skipped) {
}
