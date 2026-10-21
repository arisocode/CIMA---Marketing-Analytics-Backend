package com.cimaxis.demo.marketing.service.messages;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
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
import com.cimaxis.demo.marketing.service.portal.PortalSupport;

/**
 * Conversacion comercial entre CIMA y cada cliente, previa al proyecto.
 *
 * Cada mensaje tambien queda como interaccion sin campaña (channel "portal"):
 * el del cliente como inquiry con response = cuerpo (cuenta como respuesta en
 * KPI, embudo y ficha 360°) y el de CIMA como message (cuenta como contacto).
 */
@Service
public class MarketingMessageService {

    public static final int LIMITE_POR_HORA = 30;
    public static final String AUTOR_CIMA_POR_DEFECTO = "Equipo CIMA";
    public static final String ASUNTO_CORREO = "Tienes un nuevo mensaje de CIMA";
    private static final int LONGITUD_VISTA_PREVIA = 140;
    private static final int LONGITUD_MAXIMA_AUTOR = 120;

    private final ClientRepository clientRepository;
    private final MarketingMessageRepository messageRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final EmailService emailService;
    private final Clock clock;

    @Autowired
    public MarketingMessageService(ClientRepository clientRepository,
                                   MarketingMessageRepository messageRepository,
                                   MarketingInteractionRepository interactionRepository,
                                   EmailService emailService) {
        this(clientRepository, messageRepository, interactionRepository, emailService,
                Clock.system(PortalSupport.ZONA_CIMA));
    }

    MarketingMessageService(ClientRepository clientRepository,
                            MarketingMessageRepository messageRepository,
                            MarketingInteractionRepository interactionRepository,
                            EmailService emailService,
                            Clock clock) {
        this.clientRepository = clientRepository;
        this.messageRepository = messageRepository;
        this.interactionRepository = interactionRepository;
        this.emailService = emailService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ cliente

    /** Conversacion del cliente autenticado (ascendente); marca como leidos los mensajes de CIMA. */
    @Transactional
    public List<MessageResponse> clientMessages(String sub) {
        Client client = PortalSupport.clienteAutenticado(clientRepository, sub);
        List<MarketingMessage> mensajes = nullSafe(messageRepository.findByClientIdOrderByCreatedAtAsc(client.getClientId()));
        LocalDateTime ahora = ahora();
        List<MarketingMessage> leidos = new ArrayList<>();
        for (MarketingMessage m : mensajes) {
            if (m.getDirection() == Direction.OUTBOUND && m.getReadAt() == null) {
                m.setReadAt(ahora);
                leidos.add(m);
            }
        }
        if (!leidos.isEmpty()) messageRepository.saveAll(leidos);
        return mensajes.stream().map(MarketingMessageService::toResponse).toList();
    }

    /** Mensaje del cliente hacia CIMA. Maximo 30 por hora (429). */
    @Transactional
    public MessageResponse clientSend(String sub, String body) {
        Client client = PortalSupport.clienteAutenticado(clientRepository, sub);
        String cuerpo = validarCuerpo(body);
        LocalDateTime ahora = ahora();

        long enviadosUltimaHora = messageRepository.countByClientIdAndDirectionAndCreatedAtGreaterThanEqual(
                client.getClientId(), Direction.INBOUND, ahora.minusHours(1));
        if (enviadosUltimaHora >= LIMITE_POR_HORA) {
            throw new TooManyRequestsException("Has enviado " + LIMITE_POR_HORA
                    + " mensajes en la última hora. Intenta de nuevo más tarde.");
        }

        MarketingMessage mensaje = messageRepository.save(MarketingMessage.builder()
                .messageId(UUID.randomUUID())
                .clientId(client.getClientId())
                .direction(Direction.INBOUND)
                .body(cuerpo)
                .authorSub(client.getClientId())
                .authorName(PortalSupport.nombre(client))
                .createdAt(ahora)
                .build());

        interactionRepository.save(PortalSupport.interaccionDelPortal(client.getClientId(),
                MarketingInteraction.InteractionType.inquiry, cuerpo, client.getClientId(), ahora));
        return toResponse(mensaje);
    }

    // ------------------------------------------------------------------ CIMA

    /** Una fila por cliente con conversacion: primero las que tienen mensajes sin leer, luego la mas reciente. */
    public List<MessageThreadResponse> threads() {
        Map<String, List<MarketingMessage>> porCliente = new LinkedHashMap<>();
        for (MarketingMessage m : nullSafe(messageRepository.findAllByOrderByCreatedAtAsc())) {
            porCliente.computeIfAbsent(m.getClientId(), k -> new ArrayList<>()).add(m);
        }
        Map<String, Client> clientes = new HashMap<>();
        if (!porCliente.isEmpty()) {
            for (Client c : nullSafe(clientRepository.findAllById(porCliente.keySet()))) {
                clientes.put(c.getClientId(), c);
            }
        }

        List<MessageThreadResponse> hilos = new ArrayList<>();
        porCliente.forEach((clientId, mensajes) -> {
            MarketingMessage ultimo = mensajes.get(mensajes.size() - 1);
            long sinLeer = mensajes.stream()
                    .filter(m -> m.getDirection() == Direction.INBOUND && m.getReadAt() == null).count();
            Client c = clientes.get(clientId);
            hilos.add(new MessageThreadResponse(clientId, c != null ? PortalSupport.nombre(c) : null,
                    c != null ? c.getPlan() : null, vistaPrevia(ultimo.getBody()), ultimo.getCreatedAt(),
                    ultimo.getDirection().name(), sinLeer));
        });
        hilos.sort(Comparator.comparing((MessageThreadResponse h) -> h.unread() == 0)
                .thenComparing(MessageThreadResponse::lastAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return hilos;
    }

    /** Conversacion con un cliente vista por CIMA; no cambia el estado de lectura. */
    public List<MessageResponse> staffMessages(String clientId) {
        Client client = PortalSupport.cliente(clientRepository, clientId);
        return nullSafe(messageRepository.findByClientIdOrderByCreatedAtAsc(client.getClientId())).stream()
                .map(MarketingMessageService::toResponse).toList();
    }

    /**
     * Mensaje de CIMA al cliente. Con alsoEmail=true tambien se envia por correo
     * (plantilla de marca, respeta el cupo diario) y el resultado queda en el
     * deliveryStatus de la interaccion.
     */
    @Transactional
    public StaffMessageResponse staffSend(String clientId, String body, Boolean alsoEmail,
                                          String authorSub, String authorName) {
        Client client = PortalSupport.cliente(clientRepository, clientId);
        String cuerpo = validarCuerpo(body);
        LocalDateTime ahora = ahora();

        MarketingMessage mensaje = messageRepository.save(MarketingMessage.builder()
                .messageId(UUID.randomUUID())
                .clientId(client.getClientId())
                .direction(Direction.OUTBOUND)
                .body(cuerpo)
                .authorSub(authorSub)
                .authorName(autor(authorName))
                .createdAt(ahora)
                .build());

        MarketingInteraction interaccion = PortalSupport.interaccionDelPortal(client.getClientId(),
                MarketingInteraction.InteractionType.message, null, authorSub, ahora);

        String estadoCorreo = null;
        String detalleCorreo = null;
        if (Boolean.TRUE.equals(alsoEmail)) {
            DispatchResult resultado = emailService.send(client.getContactInfo(), ASUNTO_CORREO, cuerpo,
                    EmailService.ORIGEN_PORTAL);
            if (resultado != null && resultado.deliveryStatus() != null) {
                estadoCorreo = resultado.deliveryStatus().name();
            } else if (resultado != null) {
                estadoCorreo = resultado.delivered() ? "QUEUED" : "FAILED";
            } else {
                estadoCorreo = "FAILED";
            }
            detalleCorreo = resultado != null ? resultado.detail() : "Sin respuesta del servicio de correo";
            interaccion.setDeliveryStatus(estadoCorreo);
            if (resultado == null || !resultado.delivered()) {
                interaccion.setDeliveryError(detalleCorreo);
            }
        }
        interactionRepository.save(interaccion);

        return new StaffMessageResponse(mensaje.getMessageId(), mensaje.getDirection().name(), mensaje.getBody(),
                mensaje.getCreatedAt(), mensaje.getReadAt(), mensaje.getAuthorName(), estadoCorreo, detalleCorreo);
    }

    /** Marca como leidos los mensajes del cliente (INBOUND). */
    @Transactional
    public MarkReadResponse markRead(String clientId) {
        Client client = PortalSupport.cliente(clientRepository, clientId);
        List<MarketingMessage> pendientes = nullSafe(messageRepository.findByClientIdAndDirectionAndReadAtIsNull(
                client.getClientId(), Direction.INBOUND));
        LocalDateTime ahora = ahora();
        pendientes.forEach(m -> m.setReadAt(ahora));
        if (!pendientes.isEmpty()) messageRepository.saveAll(pendientes);
        return new MarkReadResponse(pendientes.size());
    }

    // ------------------------------------------------------------------

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(PortalSupport.ZONA_CIMA));
    }

    static String validarCuerpo(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío");
        }
        String cuerpo = body.strip();
        if (cuerpo.length() > MarketingMessage.LONGITUD_MAXIMA_CUERPO) {
            throw new IllegalArgumentException("El mensaje admite máximo "
                    + MarketingMessage.LONGITUD_MAXIMA_CUERPO + " caracteres");
        }
        return cuerpo;
    }

    private static String autor(String authorName) {
        if (authorName == null || authorName.isBlank()) return AUTOR_CIMA_POR_DEFECTO;
        String nombre = authorName.trim();
        return nombre.length() > LONGITUD_MAXIMA_AUTOR ? nombre.substring(0, LONGITUD_MAXIMA_AUTOR) : nombre;
    }

    private static String vistaPrevia(String body) {
        if (body == null) return null;
        String linea = body.strip().replaceAll("\\s+", " ");
        return linea.length() > LONGITUD_VISTA_PREVIA ? linea.substring(0, LONGITUD_VISTA_PREVIA - 3) + "..." : linea;
    }

    static MessageResponse toResponse(MarketingMessage m) {
        return new MessageResponse(m.getMessageId(), m.getDirection() != null ? m.getDirection().name() : null,
                m.getBody(), m.getCreatedAt(), m.getReadAt(), m.getAuthorName());
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }
}
