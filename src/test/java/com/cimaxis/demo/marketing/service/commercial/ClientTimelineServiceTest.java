package com.cimaxis.demo.marketing.service.commercial;

import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.RELOJ;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.cliente;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.dia;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.mensaje;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.propuesta;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.dto.commercial.ClientTimelineResponse;
import com.cimaxis.demo.marketing.dto.commercial.TimelineEvent;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.service.campaigns.CampaignAudienceService;

/** Ficha 360° del cliente: datos, estadisticas y linea de tiempo descendente. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientTimelineServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignAudienceService audienceService;

    private ClientTimelineService service;

    @BeforeEach
    void setUp() {
        Client ana = cliente("c1", "Ana", "ana@cima.co", "Oro", dia(9, 1));
        when(clientRepository.findById("c1")).thenReturn(Optional.of(ana));
        when(clientRepository.findById("zz")).thenReturn(Optional.empty());
        when(interactionRepository.findByClientId("c1")).thenReturn(List.of(
                mensaje("c1", dia(10, 10), "QUEUED"),
                respuesta("c1", dia(10, 12), "Me interesa"),
                mensaje("c1", dia(10, 16), "SKIPPED_LIMIT")));
        when(proposalRepository.findByClientId("c1")).thenReturn(List.of(
                propuesta(1, "c1", ProposalStatus.Approved, LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 14), "1000", "p-x"),
                propuesta(5, "c1", ProposalStatus.Sent, LocalDate.of(2026, 10, 18), null, "500", null)));
        when(projectRepository.findByClientId("c1")).thenReturn(List.of());
        when(audienceService.campaignsForClient("c1")).thenReturn(List.of(Campaign.builder()
                .campaignId(7).campaignName("Lanzamiento").campaignType(Campaign.CampaignType.Direct_sales)
                .status(Campaign.CampaignStatus.Active).startDate(LocalDate.of(2026, 10, 1)).build()));
        when(campaignRepository.findAllById(any())).thenReturn(List.of(Campaign.builder()
                .campaignId(1).campaignName("Bienvenida").campaignType(Campaign.CampaignType.Value_content)
                .status(Campaign.CampaignStatus.Completed).startDate(LocalDate.of(2026, 9, 15)).build()));

        ClientScoreService scoreService = new ClientScoreService(
                interactionRepository, proposalRepository, projectRepository, RELOJ);
        service = new ClientTimelineService(clientRepository, interactionRepository, proposalRepository,
                projectRepository, campaignRepository, scoreService, audienceService);
    }

    @Test
    @DisplayName("Datos del cliente: email = contactInfo, ultimo contacto sin SKIPPED_LIMIT y puntaje")
    void datosDelCliente() {
        ClientTimelineResponse ficha = service.timeline("c1");

        assertThat(ficha.client().name()).isEqualTo("Ana");
        assertThat(ficha.client().email()).isEqualTo("ana@cima.co");
        assertThat(ficha.client().plan()).isEqualTo("Oro");
        assertThat(ficha.client().createdAt()).isEqualTo(dia(9, 1));
        assertThat(ficha.client().lastContact()).isEqualTo(dia(10, 12));
        // +30 respuesta, +20 propuesta abierta, +30 aprobada, +20 proyecto vinculado
        assertThat(ficha.client().score()).isEqualTo(100);
    }

    @Test
    @DisplayName("Estadisticas con las definiciones del KPI")
    void estadisticas() {
        ClientTimelineResponse ficha = service.timeline("c1");

        assertThat(ficha.stats().interactions()).isEqualTo(2L);
        assertThat(ficha.stats().responses()).isEqualTo(1L);
        assertThat(ficha.stats().proposals()).isEqualTo(2L);
        assertThat(ficha.stats().approvedValue()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(ficha.stats().projects()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Eventos en orden descendente con todos los tipos")
    void eventosDescendentes() {
        List<TimelineEvent> eventos = service.timeline("c1").events();

        assertThat(eventos.stream().map(TimelineEvent::type).toList()).containsExactly(
                "PROPOSAL", "MESSAGE", "PROJECT", "PROPOSAL_DECISION", "RESPONSE", "MESSAGE",
                "CAMPAIGN", "PROPOSAL", "CAMPAIGN", "PLAN");
        TimelineEvent omitido = eventos.get(1);
        assertThat(omitido.detail()).contains("Estado de entrega: SKIPPED_LIMIT");
        assertThat(eventos.get(2).refId()).isEqualTo("p-x");
        assertThat(eventos.get(3).title()).isEqualTo("Propuesta aprobada: Propuesta 1");
        assertThat(eventos.get(4).detail()).contains("Me interesa");
        assertThat(eventos.get(9).title()).isEqualTo("Plan Oro");
    }

    @Test
    @DisplayName("Cliente inexistente: 404")
    void clienteInexistente() {
        assertThatThrownBy(() -> service.timeline("zz")).isInstanceOf(ResourceNotFoundException.class);
    }
}
