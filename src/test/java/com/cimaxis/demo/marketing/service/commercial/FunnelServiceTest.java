package com.cimaxis.demo.marketing.service.commercial;

import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.RELOJ;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.cliente;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.deTipo;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.dia;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.mensaje;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.propuesta;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.proyecto;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction.InteractionType;
import com.cimaxis.demo.marketing.dto.commercial.FunnelClientResponse;
import com.cimaxis.demo.marketing.dto.commercial.FunnelResponse;
import com.cimaxis.demo.marketing.dto.commercial.FunnelStage;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Embudo comercial. Datos: 4 clientes (c3 creado en noviembre, c4 sin fecha de alta),
 * un correo SKIPPED_LIMIT que no cuenta como contacto y propuestas en varios estados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunnelServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private ProjectRepository projectRepository;

    private FunnelService service;
    private List<Client> clientes;
    private List<MarketingInteraction> interacciones;
    private List<Proposal> propuestas;
    private List<Project> proyectos;

    @BeforeEach
    void setUp() {
        clientes = new ArrayList<>(List.of(
                cliente("c1", "Ana", "ana@cima.co", "Oro", dia(9, 1)),
                cliente("c2", "Beto", "beto@cima.co", "Platinum", dia(10, 5)),
                cliente("c3", "Ceci", "ceci@cima.co", null, LocalDateTime.of(2026, 11, 2, 8, 0)),
                cliente("c4", null, "dora@cima.co", null, null)));
        interacciones = new ArrayList<>(List.of(
                mensaje("c1", dia(10, 10), "QUEUED"),
                respuesta("c1", dia(10, 12), "Me interesa"),
                mensaje("c2", dia(10, 15), "SKIPPED_LIMIT"),
                deTipo("c3", dia(9, 20), InteractionType.click),
                mensaje("c4", dia(10, 2), null)));
        propuestas = new ArrayList<>(List.of(
                propuesta(1, "c1", ProposalStatus.Approved, fecha(10, 1), fecha(10, 14), "1000", "p-x"),
                propuesta(2, "c2", ProposalStatus.Sent, fecha(10, 3), null, "500", null),
                propuesta(3, "c3", ProposalStatus.In_diagnosis, fecha(10, 4), null, "300", null),
                propuesta(4, "c4", ProposalStatus.Rejected, fecha(9, 10), fecha(9, 15), "200", null)));
        proyectos = new ArrayList<>(List.of(proyecto("pr1", "c4", dia(9, 20))));

        when(clientRepository.findAll()).thenAnswer(inv -> clientes);
        when(interactionRepository.findAll()).thenAnswer(inv -> interacciones);
        when(interactionRepository.findByContactDateBetween(any(), any())).thenAnswer(inv -> {
            LocalDateTime desde = inv.getArgument(0);
            LocalDateTime hasta = inv.getArgument(1);
            return interacciones.stream()
                    .filter(i -> !i.getContactDate().isBefore(desde) && !i.getContactDate().isAfter(hasta))
                    .toList();
        });
        when(proposalRepository.findAll()).thenAnswer(inv -> propuestas);
        when(projectRepository.findAll()).thenAnswer(inv -> proyectos);

        ClientScoreService scoreService = new ClientScoreService(
                interactionRepository, proposalRepository, projectRepository, RELOJ);
        service = new FunnelService(clientRepository, interactionRepository, proposalRepository,
                projectRepository, scoreService);
    }

    @Test
    @DisplayName("Acumulado historico: conjuntos de clientes distintos por etapa y conversiones")
    void embudoHistorico() {
        FunnelResponse embudo = service.funnel(null);

        assertThat(embudo.period()).isNull();
        assertThat(embudo.stages().stream().map(FunnelStage::key).toList()).containsExactly(
                "clients", "contacted", "responded", "proposal_sent", "approved", "project");
        assertThat(embudo.stages().stream().map(FunnelStage::label).toList()).containsExactly(
                "Clientes", "Contactados", "Respondieron", "Propuesta enviada", "Aprobada", "Con proyecto");
        // c2 solo tiene un correo SKIPPED_LIMIT: no esta contactado.
        assertThat(conteos(embudo)).containsExactly(4L, 3L, 2L, 3L, 1L, 2L);
        assertThat(embudo.conversionPercent()).containsExactly(75.0, 66.67, 150.0, 33.33, 200.0);
    }

    @Test
    @DisplayName("Con periodo todo se filtra al mes, con las mismas fechas que el KPI")
    void embudoDelMes() {
        FunnelResponse embudo = service.funnel("2026-10");

        assertThat(embudo.period()).isEqualTo("2026-10");
        // clients: c1, c2 y c4 (sin fecha); c3 se creo en noviembre.
        // contacted: c1 y c4 (correo de c2 omitido); responded: c1.
        // proposal_sent: 1 y 2 creadas en octubre; approved y project: propuesta 1 respondida en octubre.
        assertThat(conteos(embudo)).containsExactly(3L, 2L, 1L, 2L, 1L, 1L);
        assertThat(embudo.conversionPercent()).containsExactly(66.67, 50.0, 200.0, 50.0, 100.0);
    }

    @Test
    @DisplayName("La conversion es null cuando la etapa anterior tiene 0 clientes")
    void conversionNullConCero() {
        clientes.clear();
        interacciones.clear();
        propuestas.clear();
        proyectos.clear();

        FunnelResponse embudo = service.funnel(null);

        assertThat(conteos(embudo)).containsExactly(0L, 0L, 0L, 0L, 0L, 0L);
        assertThat(embudo.conversionPercent()).containsExactly(null, null, null, null, null);
    }

    @Test
    @DisplayName("Clientes de una etapa: nombre (o correo), plan, ultimo contacto y puntaje, de mayor a menor")
    void clientesDeEtapa() {
        List<FunnelClientResponse> contactados = service.stageClients("contacted", null);

        assertThat(contactados.stream().map(FunnelClientResponse::clientId).toList())
                .containsExactly("c1", "c4", "c3");
        FunnelClientResponse ana = contactados.get(0);
        assertThat(ana.name()).isEqualTo("Ana");
        assertThat(ana.plan()).isEqualTo("Oro");
        assertThat(ana.lastContact()).isEqualTo(dia(10, 12));
        // +30 respuesta reciente, +30 aprobada, +20 proyecto vinculado
        assertThat(ana.score()).isEqualTo(80);
        // c4: 19 dias sin contacto (-4) y proyecto en PROJECTS (+20); nombre = contactInfo
        assertThat(contactados.get(1).name()).isEqualTo("dora@cima.co");
        assertThat(contactados.get(1).score()).isEqualTo(16);
        assertThat(contactados.get(2).score()).isEqualTo(0);
    }

    @Test
    @DisplayName("Clientes de la etapa con periodo usan el mismo conjunto del embudo")
    void clientesDeEtapaConPeriodo() {
        assertThat(service.stageClients("proposal_sent", "2026-10").stream()
                .map(FunnelClientResponse::clientId).toList()).containsExactly("c1", "c2");
    }

    @Test
    @DisplayName("Etapa o periodo invalidos responden 400 con mensaje en español")
    void validaciones() {
        assertThatThrownBy(() -> service.stageClients("leads", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Etapa inválida");
        assertThatThrownBy(() -> service.funnel("octubre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato esperado YYYY-MM");
    }

    private static List<Long> conteos(FunnelResponse embudo) {
        return embudo.stages().stream().map(FunnelStage::count).toList();
    }

    private static LocalDate fecha(int mes, int dia) {
        return LocalDate.of(2026, mes, dia);
    }
}
