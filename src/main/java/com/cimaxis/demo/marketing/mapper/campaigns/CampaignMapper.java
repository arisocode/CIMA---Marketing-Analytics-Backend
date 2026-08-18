package com.cimaxis.demo.marketing.mapper.campaigns;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignRequest;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignResponse;

/**
 * Traduccion entre la entidad Campaign y sus DTOs.
 */
@Component
public class CampaignMapper {

    public CampaignResponse toResponse(Campaign campaign) {
        if (campaign == null) {
            return null;
        }
        return CampaignResponse.builder()
                .campaignId(campaign.getCampaignId())
                .campaignName(campaign.getCampaignName())
                .campaignType(campaign.getCampaignType() != null
                        ? campaign.getCampaignType().name() : null)
                .clientId(campaign.getClientId())
                .projectId(campaign.getProjectId())
                .createdBy(campaign.getCreatedBy())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .status(campaign.getStatus() != null ? campaign.getStatus().name() : null)
                .platforms(campaign.getPlatforms())
                .objective(campaign.getObjective())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    public List<CampaignResponse> toResponseList(List<Campaign> campaigns) {
        if (campaigns == null || campaigns.isEmpty()) {
            return Collections.emptyList();
        }
        return campaigns.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Construye una entidad nueva. Los campos de auditoria los pone el servicio. */
    public Campaign toEntity(CampaignRequest request) {
        return Campaign.builder()
                .campaignName(request.getCampaignName())
                .campaignType(parseType(request.getCampaignType()))
                .clientId(request.getClientId())
                .projectId(request.getProjectId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(parseStatus(request.getStatus()))
                .platforms(request.getPlatforms())
                .objective(request.getObjective())
                .build();
    }

    /**
     * Vuelca los campos modificables sobre una campana existente.
     */
    public void aplicarCambios(CampaignRequest request, Campaign destino) {
        destino.setCampaignName(request.getCampaignName());
        destino.setCampaignType(parseType(request.getCampaignType()));
        destino.setStatus(parseStatus(request.getStatus()));
        destino.setStartDate(request.getStartDate());
        destino.setEndDate(request.getEndDate());
        destino.setPlatforms(request.getPlatforms());
        destino.setObjective(request.getObjective());
    }

    public Campaign.CampaignType parseType(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Campaign.CampaignType.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de campana invalido: " + valor
                    + ". Valores permitidos: " + Arrays.toString(Campaign.CampaignType.values()));
        }
    }

    public Campaign.CampaignStatus parseStatus(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Campaign.CampaignStatus.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de campana invalido: " + valor
                    + ". Valores permitidos: " + Arrays.toString(Campaign.CampaignStatus.values()));
        }
    }
}
