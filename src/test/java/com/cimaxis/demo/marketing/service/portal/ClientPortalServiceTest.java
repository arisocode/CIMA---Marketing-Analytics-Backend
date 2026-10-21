package com.cimaxis.demo.marketing.service.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.messages.MarketingMessage;
import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.dto.portal.ClientProposalResponse;
import com.cimaxis.demo.marketing.dto.portal.ClientSummaryResponse;
import com.cimaxis.demo.marketing.dto.portal.ProposalDecisionRequest;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.messages.MarketingMessageRepository;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.NotificationDispatcher;

/**
 * Portal del cliente: propuestas visibles, decision y resumen. Ahora = 2026-10-22T03:00Z,
 * que en Bogota todavia es el 21 de octubre a las 22:00.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientPortalServiceTest {

    private static final String ANA = "11111111-1111-4111-8111-111111111111";
    private static final String LUIS = "22222222-2222-4222-8222-222222222222";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneOffset.UTC);

    @Mock private ClientRepository clientRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private MarketingMessageRepository messageRepository;
    @Mock private MarketingPlanRepository planRepository;
    @Mock private NotificationDispatcher notificationDispatcher;

    private ClientPortalService service;
    private List<Proposal> propuestasDeAna;

    @BeforeEach
    void setUp() {
        Client ana = Client.builder().clientId(ANA).userId(ANA).additionalInfo("Ana Pérez")
                .contactInfo("ana@cima.co").plan("Oro").build();
        when(clientRepository.findById(ANA)).thenReturn(Optional.of(ana));
        when(clientRepository.findById("no-sincronizado")).thenReturn(Optional.empty());

        propuestasDeAna = new ArrayList<>(List.of(
                propuesta(1, ANA, ProposalStatus.Approved, LocalDate.of(2026, 9, 1)),
                propuesta(2, ANA, ProposalStatus.In_diagnosis, LocalDate.of(2026, 10, 20)),
                propuesta(3, ANA, ProposalStatus.Sent, LocalDate.of(2026, 10, 1)),
                propuesta(4, ANA, ProposalStatus.Rejected, LocalDate.of(2026, 10, 15)),
                propuesta(5, ANA, ProposalStatus.In_negotiation, LocalDate.of(2026, 10, 10))));
        when(proposalRepository.findByClientId(ANA)).thenReturn(propuestasDeAna);
        for (Proposal p : propuestasDeAna) {
            when(proposalRepository.findById(p.getProposalId())).thenReturn(Optional.of(p));
        }
        when(proposalRepository.findById(9)).thenReturn(Optional.of(propuesta(9, LUIS, ProposalStatus.Sent,
                LocalDate.of(2026, 10, 5))));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationDispatcher.notifyAdmin(anyString(), anyString()))
                .thenReturn(DispatchResult.simulated("admin_notification", "ok"));

        service = new ClientPortalService(clientRepository, proposalRepository, interactionRepository,
                messageRepository, planRepository, notificationDispatcher, RELOJ);
    }

    // ---------------------------------------------------------------- listado

    @Test
    @DisplayName("El cliente ve sus propuestas sin las de diagnostico: pendientes primero y luego por fecha")
    void listaPropuestasVisibles() {
        List<ClientProposalResponse> propuestas = service.proposals(ANA);

        assertThat(propuestas.stream().map(ClientProposalResponse::proposalId).toList())
                .containsExactly(5, 3, 4, 1);
        ClientProposalResponse primera = propuestas.get(0);
        assertThat(primera.title()).isEqualTo("Propuesta 5");
        assertThat(primera.value()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(primera.status()).isEqualTo("In_negotiation");
    }

    @Test
    @DisplayName("Cuenta sin cliente sincronizado: 404 con mensaje claro")
    void cuentaNoSincronizada() {
        assertThatThrownBy(() -> service.proposals("no-sincronizado"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tu cuenta aún no está sincronizada con marketing");
    }

    // ---------------------------------------------------------------- decision

    @Test
    @DisplayName("Aceptar deja la propuesta Approved con responseDate de hoy en Bogota (igual que CIMA)")
    void aceptar() {
        ClientProposalResponse respuesta = service.decide(ANA, 3, new ProposalDecisionRequest("ACCEPTED", " Perfecto "));

        Proposal p = propuestasDeAna.get(2);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.Approved);
        assertThat(p.getResponseDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(p.getDecidedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 22, 0));
        assertThat(p.getClientComment()).isEqualTo("Perfecto");
        assertThat(respuesta.status()).isEqualTo("Approved");
        assertThat(respuesta.clientComment()).isEqualTo("Perfecto");
    }

    @Test
    @DisplayName("La decision queda como interaccion inquiry del portal, sin campaña, que cuenta como respuesta")
    void registraInteraccion() {
        service.decide(ANA, 5, new ProposalDecisionRequest("rejected", "Muy costosa"));

        ArgumentCaptor<MarketingInteraction> captor = ArgumentCaptor.forClass(MarketingInteraction.class);
        verify(interactionRepository).save(captor.capture());
        MarketingInteraction interaccion = captor.getValue();
        assertThat(interaccion.getCampaignId()).isNull();
        assertThat(interaccion.getClientId()).isEqualTo(ANA);
        assertThat(interaccion.getInteractionType()).isEqualTo(MarketingInteraction.InteractionType.inquiry);
        assertThat(interaccion.getChannel()).isEqualTo("portal");
        assertThat(interaccion.getResponse()).isEqualTo("Rechazó la propuesta #5: Muy costosa");
        assertThat(KpiCalculationService.esContacto(interaccion)).isTrue();
        assertThat(KpiCalculationService.esRespuesta(interaccion)).isTrue();
        assertThat(propuestasDeAna.get(4).getStatus()).isEqualTo(ProposalStatus.Rejected);
    }

    @Test
    @DisplayName("Se avisa a CIMA con cliente, propuesta, valor y comentario")
    void avisaACima() {
        service.decide(ANA, 3, new ProposalDecisionRequest("ACCEPTED", "Adelante"));

        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).notifyAdmin(asunto.capture(), cuerpo.capture());
        assertThat(asunto.getValue()).contains("Propuesta aceptada por Ana Pérez (#3)");
        assertThat(cuerpo.getValue()).contains("Cliente: Ana Pérez (" + ANA + ")");
        assertThat(cuerpo.getValue()).contains("Propuesta: #3 - Propuesta 3");
        assertThat(cuerpo.getValue()).contains("Valor: 1500.00");
        assertThat(cuerpo.getValue()).contains("Comentario: Adelante");
    }

    @Test
    @DisplayName("Si el aviso a CIMA falla, la decision igual se guarda")
    void avisoFallidoNoBloquea() {
        when(notificationDispatcher.notifyAdmin(anyString(), anyString())).thenThrow(new RuntimeException("sin red"));

        ClientProposalResponse respuesta = service.decide(ANA, 3, new ProposalDecisionRequest("ACCEPTED", null));

        assertThat(respuesta.status()).isEqualTo("Approved");
        assertThat(respuesta.clientComment()).isNull();
    }

    @Test
    @DisplayName("Rechazar exige comentario")
    void rechazoSinComentario() {
        assertThatThrownBy(() -> service.decide(ANA, 3, new ProposalDecisionRequest("REJECTED", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comentario es obligatorio");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("El comentario admite maximo 1000 caracteres")
    void comentarioLargo() {
        assertThatThrownBy(() -> service.decide(ANA, 3, new ProposalDecisionRequest("ACCEPTED", "x".repeat(1001))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("Una decision distinta de ACCEPTED o REJECTED es 400")
    void decisionInvalida() {
        assertThatThrownBy(() -> service.decide(ANA, 3, new ProposalDecisionRequest("MAYBE", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCEPTED o REJECTED");
    }

    @Test
    @DisplayName("Una propuesta ya decidida responde 409 (IllegalStateException)")
    void yaDecidida() {
        assertThatThrownBy(() -> service.decide(ANA, 1, new ProposalDecisionRequest("ACCEPTED", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya no está pendiente");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("La propuesta de otro cliente responde 404, no 403")
    void propuestaDeOtroCliente() {
        assertThatThrownBy(() -> service.decide(ANA, 9, new ProposalDecisionRequest("ACCEPTED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(proposalRepository, never()).save(any());
        verify(interactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una propuesta propia en diagnostico tampoco existe para el cliente")
    void propuestaEnDiagnostico() {
        assertThatThrownBy(() -> service.decide(ANA, 2, new ProposalDecisionRequest("ACCEPTED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- resumen

    @Test
    @DisplayName("Resumen: nombre, plan con color, pendientes, no leidos y aprobadas")
    void resumen() {
        when(planRepository.findByNameIgnoreCase("Oro")).thenReturn(Optional.of(
                MarketingPlan.builder().name("Oro").color("#D97706").build()));
        when(messageRepository.countByClientIdAndDirectionAndReadAtIsNull(ANA, MarketingMessage.Direction.OUTBOUND))
                .thenReturn(2L);

        ClientSummaryResponse resumen = service.summary(ANA);

        assertThat(resumen).isEqualTo(new ClientSummaryResponse("Ana Pérez", "Oro", "#D97706", 2L, 2L, 1L));
    }

    private static Proposal propuesta(int id, String clientId, ProposalStatus estado, LocalDate creada) {
        return Proposal.builder().proposalId(id).clientId(clientId).status(estado)
                .description("Propuesta " + id + "\nDetalle").estimatedValue(new BigDecimal("1500.00"))
                .createdDate(creada).createdAt(creada.atStartOfDay()).build();
    }
}
