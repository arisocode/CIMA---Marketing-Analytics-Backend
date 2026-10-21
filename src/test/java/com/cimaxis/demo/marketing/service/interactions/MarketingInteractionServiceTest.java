package com.cimaxis.demo.marketing.service.interactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionRequest;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionResponse;
import com.cimaxis.demo.marketing.mapper.interactions.MarketingInteractionMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/** Registro manual de interacciones: la campaña es opcional desde que campaign_id es nullable. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketingInteractionServiceTest {

    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private CampaignRepository campaignRepository;

    private MarketingInteractionService service;

    @BeforeEach
    void setUp() {
        when(interactionRepository.save(any(MarketingInteraction.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new MarketingInteractionService(interactionRepository, campaignRepository,
                new MarketingInteractionMapper());
    }

    @Test
    @DisplayName("Sin campaña se registra y campaignId llega null en la respuesta")
    void sinCampana() {
        MarketingInteractionResponse respuesta = service.registerManual(MarketingInteractionRequest.builder()
                .clientId("c-1").interactionType("message").build(), "worker-1");

        assertThat(respuesta.getCampaignId()).isNull();
        assertThat(respuesta.getChannel()).isEqualTo("manual");
    }

    @Test
    @DisplayName("Con una campaña inexistente sigue respondiendo 404")
    void campanaInexistente() {
        when(campaignRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> service.registerManual(MarketingInteractionRequest.builder()
                .campaignId(99).clientId("c-1").build(), "worker-1"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(interactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con una campaña existente la conserva")
    void conCampana() {
        when(campaignRepository.existsById(3)).thenReturn(true);

        assertThat(service.registerManual(MarketingInteractionRequest.builder()
                .campaignId(3).clientId("c-1").build(), "worker-1").getCampaignId()).isEqualTo(3);
    }
}
