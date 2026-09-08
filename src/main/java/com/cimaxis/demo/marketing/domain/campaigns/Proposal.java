package com.cimaxis.demo.marketing.domain.campaigns;

import java.math.BigDecimal;
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

/**
 * Entidad que mapea la tabla PROPOSALS.
 * Para registrar propuestas asociadas a un cliente y
 * registrar el estado de una propuesta. Ademas alimenta el KPI
 * de ingresos estimados del modulo de Business Intelligence.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "PROPOSALS")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proposal_id")
    private Integer proposalId;

    @Column(name = "client_id", nullable = false, length = 36)
    private String clientId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProposalStatus status;

    @Column(name = "estimated_value", precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "created_date")
    private LocalDate createdDate;

    @Column(name = "response_date")
    private LocalDate responseDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    public enum ProposalStatus {
        In_diagnosis, Sent, In_negotiation, Approved, Rejected
    }
}
