package com.cimaxis.demo.marketing.mapper.interactions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionRequest;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionResponse;

@Component
public class MarketingInteractionMapper {

    public MarketingInteractionResponse toResponse(MarketingInteraction interaction) {
        if (interaction == null) {
            return null;
        }
        return MarketingInteractionResponse.builder()
                .interactionId(interaction.getInteractionId())
                .campaignId(interaction.getCampaignId())
                .clientId(interaction.getClientId())
                .executionId(interaction.getExecutionId())
                .loggedBy(interaction.getLoggedBy())
                .contactDate(interaction.getContactDate())
                .interactionType(interaction.getInteractionType() != null
                        ? interaction.getInteractionType().name() : null)
                .channel(interaction.getChannel())
                .response(interaction.getResponse())
                .build();
    }

    public List<MarketingInteractionResponse> toResponseList(List<MarketingInteraction> interactions) {
        if (interactions == null || interactions.isEmpty()) {
            return Collections.emptyList();
        }
        return interactions.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public MarketingInteraction toEntity(MarketingInteractionRequest request) {
        return MarketingInteraction.builder()
                .campaignId(request.getCampaignId())
                .clientId(request.getClientId())
                .contactDate(request.getContactDate())
                .interactionType(parseType(request.getInteractionType()))
                .channel(request.getChannel())
                .response(request.getResponse())
                .build();
    }

    public MarketingInteraction.InteractionType parseType(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return MarketingInteraction.InteractionType.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de interaccion invalido: " + valor
                    + ". Valores permitidos: "
                    + Arrays.toString(MarketingInteraction.InteractionType.values()));
        }
    }
}
