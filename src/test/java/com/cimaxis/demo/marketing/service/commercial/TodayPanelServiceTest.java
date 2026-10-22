package com.cimaxis.demo.marketing.service.commercial;

import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.RELOJ;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.cliente;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.dia;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.mensaje;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.propuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.commercial.TodayResponse;
import com.cimaxis.demo.marketing.dto.whatsapp.WhatsappPendingItem;
import com.cimaxis.demo.marketing.service.whatsapp.WhatsappTaskService;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/** Panel Hoy. Hoy = 2026-10-21. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodayPanelServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WhatsappTaskService whatsappTaskService;

    private TodayPanelService service;
    private List<Client> clientes;
    private List<MarketingInteraction> interacciones;
    private List<Proposal> propuestas;

    @BeforeEach
    void setUp() {
        clientes = new ArrayList<>(List.of(
                cliente("c1", "Ana", "ana@cima.co", "Oro", dia(9, 1)),
                cliente("c2", "Beto", "beto@cima.co", "Platinum", dia(9, 1)),
                cliente("c3", "Ceci", "ceci@cima.co", null, dia(9, 1)),
                cliente("c4", "Dora", "dora@cima.co", null, dia(9, 1))));
        interacciones = new ArrayList<>(List.of(
                mensaje("c1", dia(10, 12), "QUEUED"),
                mensaje("c2", dia(10, 20), "SKIPPED_LIMIT"),
                mensaje("c3", dia(9, 20), "QUEUED"),
                mensaje("c4", dia(10, 2), "QUEUED")));
        propuestas = new ArrayList<>(List.of(
                propuesta(2, "c2", ProposalStatus.Sent, fecha(10, 3), null, "500", null),
                propuesta(5, "c1", ProposalStatus.Sent, fecha(10, 18), null, "300", null),
                propuesta(6, "c3", ProposalStatus.In_negotiation, fecha(10, 10), null, "800", null),
                propuesta(1, "c1", ProposalStatus.Approved, fecha(9, 1), fecha(9, 20), "1000", "p-x"),
                propuesta(7, "c4", ProposalStatus.Approved, fecha(9, 1), fecha(10, 5), "700", null),
                propuesta(8, "c3", ProposalStatus.Approved, fecha(9, 1), fecha(10, 1), "900", " ")));

        when(clientRepository.findAll()).thenAnswer(inv -> clientes);
        when(interactionRepository.findAll()).thenAnswer(inv -> interacciones);
        when(proposalRepository.findAll()).thenAnswer(inv -> propuestas);
        when(projectRepository.findAll()).thenReturn(List.of());

        ClientScoreService scoreService = new ClientScoreService(
                interactionRepository, proposalRepository, projectRepository, RELOJ);
        when(whatsappTaskService.pending()).thenReturn(List.of(new WhatsappPendingItem(70, "c3", "Ceci",
                "Hola Ceci, ¿hablamos?", dia(10, 20), "Reactivación")));
        service = new TodayPanelService(clientRepository, proposalRepository, scoreService, whatsappTaskService);
    }

    @Test
    @DisplayName("Clientes sin contacto: nunca contactados primero, luego de mayor a menor")
    void clientesSinContacto() {
        TodayResponse hoy = service.today(15, 7);

        assertThat(hoy.clientsWithoutContact().stream().map(TodayResponse.ClientWithoutContact::clientId).toList())
                .containsExactly("c2", "c3", "c4");
        // c2 solo tiene un correo SKIPPED_LIMIT: nunca fue contactado.
        assertThat(hoy.clientsWithoutContact().get(0).daysSinceContact()).isNull();
        assertThat(hoy.clientsWithoutContact().get(1).daysSinceContact()).isEqualTo(31L);
        assertThat(hoy.clientsWithoutContact().get(2).daysSinceContact()).isEqualTo(19L);
        assertThat(hoy.clientsWithoutContact().get(0).plan()).isEqualTo("Platinum");
        assertThat(hoy.counts().clientsWithoutContact()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Propuestas Sent o In_negotiation abiertas hace mas de N dias")
    void propuestasSinRespuesta() {
        TodayResponse hoy = service.today(15, 7);

        assertThat(hoy.proposalsWithoutResponse().stream()
                .map(TodayResponse.ProposalWithoutResponse::proposalId).toList()).containsExactly(2, 6);
        TodayResponse.ProposalWithoutResponse primera = hoy.proposalsWithoutResponse().get(0);
        assertThat(primera.daysOpen()).isEqualTo(18L);
        assertThat(primera.clientName()).isEqualTo("Beto");
        assertThat(primera.title()).isEqualTo("Propuesta 2");
        assertThat(hoy.counts().proposalsWithoutResponse()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Aprobadas sin proyecto vinculado, la aprobada hace mas tiempo primero")
    void aprobadasSinProyecto() {
        TodayResponse hoy = service.today(15, 7);

        assertThat(hoy.approvedWithoutProject().stream()
                .map(TodayResponse.ApprovedWithoutProject::proposalId).toList()).containsExactly(8, 7);
        assertThat(hoy.counts().approvedWithoutProject()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Los umbrales se pueden ajustar")
    void umbralesPersonalizados() {
        TodayResponse hoy = service.today(30, 15);

        assertThat(hoy.clientsWithoutContact().stream().map(TodayResponse.ClientWithoutContact::clientId).toList())
                .containsExactly("c2", "c3");
        assertThat(hoy.proposalsWithoutResponse().stream()
                .map(TodayResponse.ProposalWithoutResponse::proposalId).toList()).containsExactly(2);
    }

    @Test
    @DisplayName("Cada lista se limita a 20 elementos y counts trae el total real")
    void limiteDeVeinte() {
        for (int i = 0; i < 25; i++) {
            clientes.add(cliente("n" + i, "Nuevo " + i, null, null, dia(8, 1)));
        }

        TodayResponse hoy = service.today(15, 7);

        assertThat(hoy.clientsWithoutContact()).hasSize(20);
        assertThat(hoy.counts().clientsWithoutContact()).isEqualTo(28L);
    }

    @Test
    @DisplayName("Incluye los WhatsApp pendientes de enviar a mano")
    void whatsappPendientes() {
        TodayResponse hoy = service.today(15, 7);

        assertThat(hoy.whatsappPending()).hasSize(1);
        assertThat(hoy.whatsappPending().get(0).workflowName()).isEqualTo("Reactivación");
        assertThat(hoy.counts().whatsappPending()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Un umbral negativo responde 400")
    void umbralNegativo() {
        assertThatThrownBy(() -> service.today(-1, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("daysWithoutContact");
    }

    private static LocalDate fecha(int mes, int dia) {
        return LocalDate.of(2026, mes, dia);
    }
}
