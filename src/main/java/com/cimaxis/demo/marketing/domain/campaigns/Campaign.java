package com.cimaxis.demo.marketing.domain.campaigns;

import java.time.LocalDate;
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
@Table(name = "CAMPAIGNS")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campaign_id")
    private Integer campaignId;

    @Column(name = "campaign_name", nullable = false, length = 150)
    private String campaignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false)
    private CampaignType campaignType;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CampaignStatus status;

    @Column(name = "platforms", length = 255)
    private String platforms;

    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CampaignType {
        Positioning, Direct_sales, Value_content, Testimonial, Reactivation
    }

    public enum CampaignStatus {
        Draft, Active, Paused, Completed, Cancelled
    }
}
