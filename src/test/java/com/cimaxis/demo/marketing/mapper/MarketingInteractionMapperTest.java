package com.cimaxis.demo.marketing.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionResponse;
import com.cimaxis.demo.marketing.mapper.interactions.MarketingInteractionMapper;

class MarketingInteractionMapperTest {

    @Test
    void exposesTheDeliveryStatusAndError() {
        MarketingInteraction interaccion = MarketingInteraction.builder()
                .interactionId(1).campaignId(2).clientId("c-1")
                .interactionType(MarketingInteraction.InteractionType.no_response)
                .channel("email").deliveryStatus("FAILED").deliveryError("HTTP 400")
                .build();

        MarketingInteractionResponse respuesta = new MarketingInteractionMapper().toResponse(interaccion);

        assertThat(respuesta.getDeliveryStatus()).isEqualTo("FAILED");
        assertThat(respuesta.getDeliveryError()).isEqualTo("HTTP 400");
        assertThat(respuesta.getInteractionType()).isEqualTo("no_response");
    }
}
