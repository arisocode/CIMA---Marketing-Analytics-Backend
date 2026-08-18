package com.cimaxis.demo.marketing.mapper.campaigns;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalRequest;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalResponse;

@Component
public class ProposalMapper {

    public ProposalResponse toResponse(Proposal proposal) {
        if (proposal == null) {
            return null;
        }
        return ProposalResponse.builder()
                .proposalId(proposal.getProposalId())
                .clientId(proposal.getClientId())
                .description(proposal.getDescription())
                .documentUrl(proposal.getDocumentUrl())
                .status(proposal.getStatus() != null ? proposal.getStatus().name() : null)
                .estimatedValue(proposal.getEstimatedValue())
                .createdDate(proposal.getCreatedDate())
                .responseDate(proposal.getResponseDate())
                .createdAt(proposal.getCreatedAt())
                .updatedAt(proposal.getUpdatedAt())
                .build();
    }

    public List<ProposalResponse> toResponseList(List<Proposal> proposals) {
        if (proposals == null || proposals.isEmpty()) {
            return Collections.emptyList();
        }
        return proposals.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Proposal toEntity(ProposalRequest request) {
        return Proposal.builder()
                .clientId(request.getClientId())
                .description(request.getDescription())
                .documentUrl(request.getDocumentUrl())
                .status(parseStatus(request.getStatus()))
                .estimatedValue(request.getEstimatedValue())
                .createdDate(request.getCreatedDate())
                .build();
    }

    public void aplicarCambios(ProposalRequest request, Proposal destino) {
        destino.setDescription(request.getDescription());
        destino.setDocumentUrl(request.getDocumentUrl());
        destino.setEstimatedValue(request.getEstimatedValue());
        Proposal.ProposalStatus status = parseStatus(request.getStatus());
        if (status != null) {
            destino.setStatus(status);
        }
    }

    public Proposal.ProposalStatus parseStatus(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Proposal.ProposalStatus.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de propuesta invalido: " + valor
                    + ". Valores permitidos: " + Arrays.toString(Proposal.ProposalStatus.values()));
        }
    }
}
