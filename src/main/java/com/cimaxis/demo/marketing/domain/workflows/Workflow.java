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
@Table(name = "WORKFLOWS")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_id")
    private Integer workflowId;

    @Column(name = "campaign_id", nullable = false)
    private Integer campaignId;

    @Column(name = "workflow_name", nullable = false, length = 150)
    private String workflowName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Column(name = "no_contact_days")
    private Integer noContactDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "message_template", columnDefinition = "TEXT")
    private String messageTemplate;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum TriggerType {
        scheduled_date, no_contact_x_days, proposal_no_response,
        project_completed, manual
    }

    public enum ActionType {
        send_email, send_whatsapp, log_followup, notify_admin
    }
}
