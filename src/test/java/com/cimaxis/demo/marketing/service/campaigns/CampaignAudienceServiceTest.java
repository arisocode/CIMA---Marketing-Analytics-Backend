package com.cimaxis.demo.marketing.service.campaigns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.segments.MarketingSegmentRepository;
import com.cimaxis.demo.marketing.service.segmentation.ClientSegmentationService;
import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/** Audiencia de campaña: un cliente, un segmento guardado o todos los clientes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignAudienceServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private MarketingSegmentRepository segmentRepository;
    @Mock private ClientSegmentationService segmentationService;
    @Mock private CampaignRepository campaignRepository;

    private CampaignAudienceService service;
    private final UUID segmentoId = UUID.randomUUID();
    private Campaign deCliente;
    private Campaign deSegmento;
    private Campaign deTodos;

    @BeforeEach
    void setUp() {
        when(clientRepository.findAll()).thenReturn(List.of(cliente("c1", "Ana"), cliente("c2", "Beto"), cliente("c3", "Ceci")));
        when(clientRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Ana")));
        when(segmentRepository.findById(segmentoId)).thenReturn(Optional.of(MarketingSegment.builder()
                .segmentId(segmentoId).name("Oro activos").criteria("{\"plans\":[\"Oro\"]}").active(true).build()));
        when(segmentationService.segmentIds(any(SegmentCriteria.class))).thenReturn(List.of("c2", "c3"));

        deCliente = Campaign.builder().campaignId(1).campaignName("Ana").clientId("c1").build();
        deSegmento = Campaign.builder().campaignId(2).campaignName("Oro").audienceType(Campaign.AudienceType.SEGMENT)
                .segmentId(segmentoId).build();
        deTodos = Campaign.builder().campaignId(3).campaignName("Todos").audienceType(Campaign.AudienceType.ALL).build();
        when(campaignRepository.findAll()).thenReturn(List.of(deCliente, deSegmento, deTodos));

        service = new CampaignAudienceService(clientRepository, segmentRepository, segmentationService, campaignRepository);
    }

    @Test
    @DisplayName("Una campaña sin audienceType (anterior al cambio) es CLIENT")
    void audienciaPorDefecto() {
        assertThat(deCliente.audienceOrDefault()).isEqualTo(Campaign.AudienceType.CLIENT);
        assertThat(service.resolve(deCliente)).containsExactly("c1");
    }

    @Test
    @DisplayName("SEGMENT resuelve el criterio guardado; ALL a todos los clientes")
    void resolver() {
        assertThat(service.resolve(deSegmento)).containsExactly("c2", "c3");
        assertThat(service.resolve(deTodos)).containsExactly("c1", "c2", "c3");
        assertThat(service.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("Descripción para reportes: nombre, Segmento: <nombre> o Todos los clientes")
    void describir() {
        assertThat(service.describe(deCliente, Map.of())).isEqualTo("Ana");
        assertThat(service.describe(deSegmento, Map.of())).isEqualTo("Segmento: Oro activos");
        assertThat(service.describe(deTodos, Map.of())).isEqualTo("Todos los clientes");
    }

    @Test
    @DisplayName("Campañas de un cliente: la suya, las de segmentos que lo contienen y las de todos")
    void campanasDelCliente() {
        assertThat(service.campaignsForClient("c1").stream().map(Campaign::getCampaignId).toList()).containsExactly(1, 3);
        assertThat(service.campaignsForClient("c2").stream().map(Campaign::getCampaignId).toList()).containsExactly(2, 3);
    }

    @Test
    @DisplayName("El segmento se calcula una vez por evaluación (caché por segmentId)")
    void cacheDeSegmento() {
        service.campaignsForClient("c2");
        verify(segmentationService, times(1)).segmentIds(any(SegmentCriteria.class));
    }

    @Test
    @DisplayName("Vigente = Active y hoy entre sus fechas (fin opcional)")
    void vigente() {
        Campaign c = Campaign.builder().status(Campaign.CampaignStatus.Active).startDate(LocalDate.of(2026, 10, 1)).build();
        assertThat(CampaignAudienceService.vigente(c, LocalDate.of(2026, 10, 21))).isTrue();
        assertThat(CampaignAudienceService.vigente(c, LocalDate.of(2026, 9, 30))).isFalse();
        c.setEndDate(LocalDate.of(2026, 10, 20));
        assertThat(CampaignAudienceService.vigente(c, LocalDate.of(2026, 10, 21))).isFalse();
        c.setEndDate(null);
        c.setStatus(Campaign.CampaignStatus.Paused);
        assertThat(CampaignAudienceService.vigente(c, LocalDate.of(2026, 10, 21))).isFalse();
    }

    private static Client cliente(String id, String nombre) {
        return Client.builder().clientId(id).additionalInfo(nombre).build();
    }
}
