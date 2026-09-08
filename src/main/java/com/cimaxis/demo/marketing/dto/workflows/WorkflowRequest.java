package com.cimaxis.demo.marketing.dto.workflows;

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
public class WorkflowRequest {

    private Integer campaignId;
    private String workflowName;
    private String description;
    private String triggerType;
    private Integer noContactDays;
    private String actionType;
    private String messageTemplate;
    private Boolean active;
}
