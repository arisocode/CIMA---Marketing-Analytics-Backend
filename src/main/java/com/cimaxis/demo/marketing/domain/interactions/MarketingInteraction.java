package com.cimaxis.demo.marketing.domain.interactions;
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
@Table(name = "MARKETING_INTERACTIONS")
public class MarketingInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interaction_id")
    private Integer interactionId;

    @Column(name = "campaign_id", nullable = false)
    private Integer campaignId;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(name = "execution_id")
    private Integer executionId;

    @Column(name = "logged_by")
    private Integer loggedBy;

    @Column(name = "contact_date")
    private LocalDateTime contactDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    private InteractionType interactionType;

    @Column(name = "channel", length = 80)
    private String channel;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    public enum InteractionType {
        click, message, inquiry, purchase, testimonial, no_response
    }
}
