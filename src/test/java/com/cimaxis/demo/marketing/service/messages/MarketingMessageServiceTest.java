package com.cimaxis.demo.marketing.service.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import com.cimaxis.demo.config.TooManyRequestsException;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.messages.MarketingMessage;
import com.cimaxis.demo.marketing.domain.messages.MarketingMessage.Direction;
import com.cimaxis.demo.marketing.dto.messages.MarkReadResponse;
import com.cimaxis.demo.marketing.dto.messages.MessageResponse;
import com.cimaxis.demo.marketing.dto.messages.MessageThreadResponse;
import com.cimaxis.demo.marketing.dto.messages.StaffMessageResponse;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.messages.MarketingMessageRepository;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.EmailService;

/** Conversacion comercial. Ahora = 2026-10-21 10:00 en Bogota. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketingMessageServiceTest {

    private static final String ANA = "11111111-1111-4111-8111-111111111111";
    private static final String LUIS = "22222222-2222-4222-8222-222222222222";
    private static final String WORKER = "33333333-3333-4333-8333-333333333333";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 0);

    @Mock private ClientRepository clientRepository;
    @Mock private MarketingMessageRepository messageRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private EmailService emailService;

    private MarketingMessageService service;
    private List<MarketingMessage> conversacionAna;

    @BeforeEach
    void setUp() {
        Client ana = Client.builder().clientId(ANA).userId(ANA).additionalInfo("Ana Pérez")
                .contactInfo("ana@cima.co").plan("Oro").build();
        Client luis = Client.builder().clientId(LUIS).userId(LUIS).contactInfo("luis@cima.co").build();
        when(clientRepository.findById(ANA)).thenReturn(Optional.of(ana));
        when(clientRepository.findById(LUIS)).thenReturn(Optional.of(luis));
        when(clientRepository.findById("nadie")).thenReturn(Optional.empty());
        when(clientRepository.findAllById(any())).thenReturn(List.of(ana, luis));

        conversacionAna = new ArrayList<>(List.of(
                mensaje(ANA, Direction.OUTBOUND, "Hola Ana", AHORA.minusDays(2), null),
                mensaje(ANA, Direction.INBOUND, "Hola, tengo una duda", AHORA.minusDays(1), null),
                mensaje(ANA, Direction.OUTBOUND, "Claro, cuéntanos", AHORA.minusHours(3), null)));
        when(messageRepository.findByClientIdOrderByCreatedAtAsc(ANA)).thenReturn(conversacionAna);
        when(messageRepository.save(any(MarketingMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new MarketingMessageService(clientRepository, messageRepository, interactionRepository,
                emailService, RELOJ);
    }

    // ---------------------------------------------------------------- cliente

    @Test
    @DisplayName("El cliente ve su conversacion en orden ascendente y quedan leidos los mensajes de CIMA")
    void clienteLeeSuConversacion() {
        List<MessageResponse> mensajes = service.clientMessages(ANA);

        assertThat(mensajes.stream().map(MessageResponse::body).toList())
                .containsExactly("Hola Ana", "Hola, tengo una duda", "Claro, cuéntanos");
        assertThat(conversacionAna.get(0).getReadAt()).isEqualTo(AHORA);
        assertThat(conversacionAna.get(2).getReadAt()).isEqualTo(AHORA);
        assertThat(conversacionAna.get(1).getReadAt()).isNull();
        assertThat(mensajes.get(0).direction()).isEqualTo("OUTBOUND");
    }

    @Test
    @DisplayName("El mensaje del cliente queda INBOUND y como interaccion inquiry sin campaña (cuenta como respuesta)")
    void clienteEnviaMensaje() {
        MessageResponse enviado = service.clientSend(ANA, "  ¿Cuándo empezamos?  ");

        assertThat(enviado.direction()).isEqualTo("INBOUND");
        assertThat(enviado.body()).isEqualTo("¿Cuándo empezamos?");
        assertThat(enviado.authorName()).isEqualTo("Ana Pérez");
        assertThat(enviado.createdAt()).isEqualTo(AHORA);

        ArgumentCaptor<MarketingInteraction> captor = ArgumentCaptor.forClass(MarketingInteraction.class);
        verify(interactionRepository).save(captor.capture());
        MarketingInteraction interaccion = captor.getValue();
        assertThat(interaccion.getCampaignId()).isNull();
        assertThat(interaccion.getChannel()).isEqualTo("portal");
        assertThat(interaccion.getInteractionType()).isEqualTo(MarketingInteraction.InteractionType.inquiry);
        assertThat(interaccion.getResponse()).isEqualTo("¿Cuándo empezamos?");
        assertThat(KpiCalculationService.esRespuesta(interaccion)).isTrue();
    }

    @Test
    @DisplayName("Mas de 30 mensajes por hora responde 429 y no guarda nada")
    void limitePorHora() {
        when(messageRepository.countByClientIdAndDirectionAndCreatedAtGreaterThanEqual(
                ANA, Direction.INBOUND, AHORA.minusHours(1))).thenReturn(30L);

        assertThatThrownBy(() -> service.clientSend(ANA, "Uno más"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("30 mensajes");
        verify(messageRepository, never()).save(any());
        verify(interactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mensaje vacio o de mas de 4000 caracteres es 400")
    void validaCuerpo() {
        assertThatThrownBy(() -> service.clientSend(ANA, "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacío");
        assertThatThrownBy(() -> service.clientSend(ANA, "x".repeat(4001)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4000");
    }

    @Test
    @DisplayName("Usuario client sin cliente sincronizado: 404")
    void clienteNoSincronizado() {
        assertThatThrownBy(() -> service.clientMessages("nadie"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("sincronizada");
    }

    // ---------------------------------------------------------------- CIMA

    @Test
    @DisplayName("Bandeja: primero conversaciones con no leidos y luego por ultimo mensaje")
    void bandeja() {
        when(messageRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(
                mensaje(ANA, Direction.INBOUND, "Hola, tengo una duda", AHORA.minusDays(1), null),
                mensaje(LUIS, Direction.OUTBOUND, "Hola Luis", AHORA.minusDays(3), null),
                mensaje(LUIS, Direction.INBOUND, "Gracias", AHORA.minusDays(2), AHORA.minusDays(2)),
                mensaje(LUIS, Direction.OUTBOUND, "Con gusto", AHORA.minusHours(1), null),
                mensaje(ANA, Direction.INBOUND, "¿Me responden?", AHORA.minusHours(5), null)));

        List<MessageThreadResponse> hilos = service.threads();

        assertThat(hilos.stream().map(MessageThreadResponse::clientId).toList()).containsExactly(ANA, LUIS);
        MessageThreadResponse ana = hilos.get(0);
        assertThat(ana.unread()).isEqualTo(2L);
        assertThat(ana.clientName()).isEqualTo("Ana Pérez");
        assertThat(ana.plan()).isEqualTo("Oro");
        assertThat(ana.lastMessage()).isEqualTo("¿Me responden?");
        assertThat(ana.lastDirection()).isEqualTo("INBOUND");
        MessageThreadResponse luis = hilos.get(1);
        assertThat(luis.unread()).isEqualTo(0L);
        assertThat(luis.clientName()).isEqualTo("luis@cima.co");
        assertThat(luis.lastAt()).isEqualTo(AHORA.minusHours(1));
    }

    @Test
    @DisplayName("CIMA responde sin correo: OUTBOUND con autor por defecto e interaccion message sin campaña")
    void cimaRespondeSinCorreo() {
        StaffMessageResponse enviado = service.staffSend(ANA, "Te enviamos la propuesta", false, WORKER, null);

        assertThat(enviado.direction()).isEqualTo("OUTBOUND");
        assertThat(enviado.authorName()).isEqualTo("Equipo CIMA");
        assertThat(enviado.emailDeliveryStatus()).isNull();
        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());

        ArgumentCaptor<MarketingInteraction> captor = ArgumentCaptor.forClass(MarketingInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertThat(captor.getValue().getInteractionType()).isEqualTo(MarketingInteraction.InteractionType.message);
        assertThat(captor.getValue().getCampaignId()).isNull();
        assertThat(captor.getValue().getLoggedBy()).isEqualTo(WORKER);
        assertThat(captor.getValue().getDeliveryStatus()).isNull();
    }

    @Test
    @DisplayName("Con alsoEmail se envia por correo y el resultado queda en la interaccion")
    void cimaRespondeConCorreo() {
        when(emailService.send(eq("ana@cima.co"), anyString(), eq("Hola Ana"), eq(EmailService.ORIGEN_PORTAL)))
                .thenReturn(DispatchResult.skippedByLimit("email", "Límite diario alcanzado"));

        StaffMessageResponse enviado = service.staffSend(ANA, "Hola Ana", true, WORKER, "Laura Gómez");

        assertThat(enviado.authorName()).isEqualTo("Laura Gómez");
        assertThat(enviado.emailDeliveryStatus()).isEqualTo("SKIPPED_LIMIT");
        assertThat(enviado.emailDetail()).isEqualTo("Límite diario alcanzado");
        ArgumentCaptor<MarketingInteraction> captor = ArgumentCaptor.forClass(MarketingInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo("SKIPPED_LIMIT");
        assertThat(captor.getValue().getDeliveryError()).isEqualTo("Límite diario alcanzado");
    }

    @Test
    @DisplayName("Correo encolado: QUEUED sin error en la interaccion")
    void correoEncolado() {
        when(emailService.send(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(DispatchResult.queued("email", "Encolado"));

        StaffMessageResponse enviado = service.staffSend(ANA, "Hola Ana", true, WORKER, null);

        assertThat(enviado.emailDeliveryStatus()).isEqualTo("QUEUED");
        ArgumentCaptor<MarketingInteraction> captor = ArgumentCaptor.forClass(MarketingInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryError()).isNull();
    }

    @Test
    @DisplayName("CIMA marca como leidos los mensajes del cliente")
    void marcarLeidos() {
        MarketingMessage pendiente = mensaje(ANA, Direction.INBOUND, "Hola", AHORA.minusDays(1), null);
        when(messageRepository.findByClientIdAndDirectionAndReadAtIsNull(ANA, Direction.INBOUND))
                .thenReturn(List.of(pendiente));

        MarkReadResponse resultado = service.markRead(ANA);

        assertThat(resultado.marked()).isEqualTo(1);
        assertThat(pendiente.getReadAt()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("La vista de CIMA no cambia el estado de lectura")
    void vistaDeCimaNoMarca() {
        service.staffMessages(ANA);

        assertThat(conversacionAna.get(0).getReadAt()).isNull();
        verify(messageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Cliente inexistente en la bandeja de CIMA: 404")
    void clienteInexistente() {
        assertThatThrownBy(() -> service.staffSend("nadie", "Hola", false, WORKER, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static MarketingMessage mensaje(String clientId, Direction direccion, String cuerpo,
                                            LocalDateTime fecha, LocalDateTime leido) {
        return MarketingMessage.builder().messageId(UUID.randomUUID()).clientId(clientId).direction(direccion)
                .body(cuerpo).createdAt(fecha).readAt(leido)
                .authorName(direccion == Direction.INBOUND ? "Cliente" : "Equipo CIMA").build();
    }
}
