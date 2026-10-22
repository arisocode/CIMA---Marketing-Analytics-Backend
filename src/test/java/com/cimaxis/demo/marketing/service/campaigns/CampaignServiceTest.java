package com.cimaxis.demo.marketing.service.campaigns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignRequest;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignResponse;
import com.cimaxis.demo.marketing.mapper.campaigns.CampaignMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.service.segments.SavedSegmentService;

/** Campañas con audiencia CLIENT, SEGMENT o ALL. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignAudienceService audienceService;
    @Mock private SavedSegmentService segmentService;

    private CampaignService service;
    private final UUID segmentoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
        when(audienceService.resolve(any(Campaign.class), any())).thenReturn(List.of("c1", "c2", "c3"));
        when(audienceService.segmento(any(Campaign.class))).thenReturn(Optional.of(
                MarketingSegment.builder().segmentId(segmentoId).name("Oro activos").build()));
        service = new CampaignService(campaignRepository, new CampaignMapper(), audienceService, segmentService);
    }

    @Test
    @DisplayName("Sin audienceType la campaña es CLIENT y exige el cliente, como antes")
    void clientePorDefecto() {
        CampaignResponse r = service.create(request(null, "c1", null), "worker-1");

        assertThat(r.getAudienceType()).isEqualTo("CLIENT");
        assertThat(r.getClientId()).isEqualTo("c1");
        assertThat(r.getAudienceSize()).isEqualTo(3);

        assertThatThrownBy(() -> service.create(request(null, " ", null), "worker-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cliente es obligatorio");
    }

    @Test
    @DisplayName("SEGMENT exige un segmento activo, no guarda cliente y devuelve el nombre del segmento")
    void segmento() {
        CampaignResponse r = service.create(request("segment", "c1", segmentoId), "worker-1");

        assertThat(r.getAudienceType()).isEqualTo("SEGMENT");
        assertThat(r.getClientId()).isNull();
        assertThat(r.getSegmentId()).isEqualTo(segmentoId);
        assertThat(r.getSegmentName()).isEqualTo("Oro activos");
        verify(segmentService).requireActive(segmentoId);

        assertThatThrownBy(() -> service.create(request("SEGMENT", null, null), "worker-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segmento es obligatorio");
    }

    @Test
    @DisplayName("Un segmento inactivo se rechaza")
    void segmentoInactivo() {
        when(segmentService.requireActive(segmentoId)).thenThrow(new IllegalArgumentException("El segmento 'Oro' está inactivo"));

        assertThatThrownBy(() -> service.create(request("SEGMENT", null, segmentoId), "worker-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inactivo");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("ALL no usa cliente ni segmento")
    void todos() {
        CampaignResponse r = service.create(request("ALL", "c1", segmentoId), "worker-1");

        assertThat(r.getAudienceType()).isEqualTo("ALL");
        assertThat(r.getClientId()).isNull();
        assertThat(r.getSegmentId()).isNull();
    }

    @Test
    @DisplayName("Audiencia inválida responde 400")
    void audienciaInvalida() {
        assertThatThrownBy(() -> service.create(request("TODOS", null, null), "worker-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Audiencia invalida");
    }

    @Test
    @DisplayName("Editar sin audienceType conserva la audiencia actual")
    void editarConservaAudiencia() {
        Campaign existente = Campaign.builder().campaignId(9).campaignName("Oro").audienceType(Campaign.AudienceType.SEGMENT)
                .segmentId(segmentoId).status(Campaign.CampaignStatus.Active).startDate(LocalDate.of(2026, 10, 1)).build();
        when(campaignRepository.findById(9)).thenReturn(Optional.of(existente));

        CampaignResponse r = service.update(9, request(null, null, null));

        assertThat(r.getAudienceType()).isEqualTo("SEGMENT");
        assertThat(existente.getSegmentId()).isEqualTo(segmentoId);
    }

    @Test
    @DisplayName("Filtro por cliente: campañas cuya audiencia actual lo incluye")
    void porCliente() {
        when(audienceService.campaignsForClient("c2")).thenReturn(List.of(
                Campaign.builder().campaignId(3).campaignName("Todos").audienceType(Campaign.AudienceType.ALL).build()));

        assertThat(service.findByClient("c2").get(0).getAudienceType()).isEqualTo("ALL");
    }

    private static CampaignRequest request(String audiencia, String clientId, UUID segmentId) {
        return CampaignRequest.builder().campaignName("Campaña").campaignType("Reactivation")
                .audienceType(audiencia).clientId(clientId).segmentId(segmentId)
                .status("Active").startDate(LocalDate.of(2026, 10, 1)).build();
    }
}
