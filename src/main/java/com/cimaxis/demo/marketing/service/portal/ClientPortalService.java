package com.cimaxis.demo.marketing.service.portal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.messages.MarketingMessage;
import com.cimaxis.demo.marketing.dto.portal.ClientProposalResponse;
import com.cimaxis.demo.marketing.dto.portal.ClientSummaryResponse;
import com.cimaxis.demo.marketing.dto.portal.ProposalDecisionRequest;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.messages.MarketingMessageRepository;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;
import com.cimaxis.demo.marketing.service.notifications.NotificationDispatcher;

/**
 * Portal del cliente: sus propuestas, la decision sobre ellas y su resumen.
 * El clientId siempre es el subject autenticado; una propuesta de otro cliente
 * (o en diagnostico) responde 404 para no revelar que existe.
 *
 * Una aprobacion desde el portal deja la propuesta igual que una aprobacion de
 * CIMA (status Approved y responseDate), asi que alimenta el KPI de ingresos de
 * la misma forma. La decision se registra como interaccion inquiry con texto de
 * respuesta, que cuenta como respuesta en KPI, embudo y ficha 360°.
 */
@Service
public class ClientPortalService {

    private static final Logger log = LoggerFactory.getLogger(ClientPortalService.class);

    public static final int LONGITUD_MAXIMA_COMENTARIO = 1000;

    private final ClientRepository clientRepository;
    private final ProposalRepository proposalRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final MarketingMessageRepository messageRepository;
    private final MarketingPlanRepository planRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    @Autowired
    public ClientPortalService(ClientRepository clientRepository,
                               ProposalRepository proposalRepository,
                               MarketingInteractionRepository interactionRepository,
                               MarketingMessageRepository messageRepository,
                               MarketingPlanRepository planRepository,
                               NotificationDispatcher notificationDispatcher) {
        this(clientRepository, proposalRepository, interactionRepository, messageRepository, planRepository,
                notificationDispatcher, Clock.system(PortalSupport.ZONA_CIMA));
    }

    ClientPortalService(ClientRepository clientRepository,
                        ProposalRepository proposalRepository,
                        MarketingInteractionRepository interactionRepository,
                        MarketingMessageRepository messageRepository,
                        MarketingPlanRepository planRepository,
                        NotificationDispatcher notificationDispatcher,
                        Clock clock) {
        this.clientRepository = clientRepository;
        this.proposalRepository = proposalRepository;
        this.interactionRepository = interactionRepository;
        this.messageRepository = messageRepository;
        this.planRepository = planRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.clock = clock;
    }

    /** Propuestas visibles del cliente: pendientes primero, luego la mas reciente. */
    public List<ClientProposalResponse> proposals(String sub) {
        Client client = PortalSupport.clienteAutenticado(clientRepository, sub);
        return nullSafe(proposalRepository.findByClientId(client.getClientId())).stream()
                .filter(ClientPortalService::visibleParaElCliente)
                .sorted(Comparator.comparing((Proposal p) -> !CommercialFacts.ESTADOS_ABIERTA.contains(p.getStatus()))
                        .thenComparing(ClientPortalService::fechaDeOrden, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ClientPortalService::toResponse)
                .toList();
    }

    @Transactional
    public ClientProposalResponse decide(String sub, Integer proposalId, ProposalDecisionRequest request) {
        Client client = PortalSupport.clienteAutenticado(clientRepository, sub);
        Proposal proposal = propuestaDelCliente(client, proposalId);

        String decision = request == null || request.decision() == null ? ""
                : request.decision().trim().toUpperCase(Locale.ROOT);
        boolean acepta;
        if ("ACCEPTED".equals(decision)) {
            acepta = true;
        } else if ("REJECTED".equals(decision)) {
            acepta = false;
        } else {
            throw new IllegalArgumentException("La decisión debe ser ACCEPTED o REJECTED");
        }

        String comentario = PortalSupport.texto(request.comment());
        if (!acepta && comentario == null) {
            throw new IllegalArgumentException("Cuéntanos por qué rechazas la propuesta: el comentario es obligatorio");
        }
        if (comentario != null && comentario.length() > LONGITUD_MAXIMA_COMENTARIO) {
            throw new IllegalArgumentException("El comentario admite máximo " + LONGITUD_MAXIMA_COMENTARIO + " caracteres");
        }
        if (!CommercialFacts.ESTADOS_ABIERTA.contains(proposal.getStatus())) {
            throw new IllegalStateException("La propuesta #" + proposal.getProposalId()
                    + " ya no está pendiente de decisión (estado actual: " + proposal.getStatus().name() + ")");
        }

        LocalDateTime ahora = LocalDateTime.now(clock.withZone(PortalSupport.ZONA_CIMA));
        LocalDate hoy = ahora.toLocalDate();
        proposal.setStatus(acepta ? ProposalStatus.Approved : ProposalStatus.Rejected);
        proposal.setResponseDate(hoy);
        proposal.setDecidedAt(ahora);
        proposal.setClientComment(comentario);
        proposal.setUpdatedAt(ahora);
        Proposal guardada = proposalRepository.save(proposal);

        String accion = acepta ? "Aceptó" : "Rechazó";
        String respuesta = accion + " la propuesta #" + proposal.getProposalId()
                + (comentario != null ? ": " + comentario : "");
        interactionRepository.save(PortalSupport.interaccionDelPortal(client.getClientId(),
                MarketingInteraction.InteractionType.inquiry, respuesta, client.getClientId(), ahora));

        avisarACima(client, guardada, acepta, comentario);
        return toResponse(guardada);
    }

    public ClientSummaryResponse summary(String sub) {
        Client client = PortalSupport.clienteAutenticado(clientRepository, sub);
        List<Proposal> propuestas = nullSafe(proposalRepository.findByClientId(client.getClientId()));
        String color = client.getPlan() == null ? null
                : planRepository.findByNameIgnoreCase(client.getPlan().trim()).map(p -> p.getColor()).orElse(null);
        return new ClientSummaryResponse(
                PortalSupport.nombre(client),
                client.getPlan(),
                color,
                propuestas.stream().filter(p -> CommercialFacts.ESTADOS_ABIERTA.contains(p.getStatus())).count(),
                messageRepository.countByClientIdAndDirectionAndReadAtIsNull(
                        client.getClientId(), MarketingMessage.Direction.OUTBOUND),
                propuestas.stream().filter(p -> p.getStatus() == ProposalStatus.Approved).count());
    }

    // ------------------------------------------------------------------

    private Proposal propuestaDelCliente(Client client, Integer proposalId) {
        if (proposalId == null) {
            throw ResourceNotFoundException.de("Propuesta", null);
        }
        return proposalRepository.findById(proposalId)
                .filter(p -> client.getClientId().equals(p.getClientId()))
                .filter(ClientPortalService::visibleParaElCliente)
                .orElseThrow(() -> ResourceNotFoundException.de("Propuesta", proposalId));
    }

    private void avisarACima(Client client, Proposal proposal, boolean acepta, String comentario) {
        String nombre = PortalSupport.nombre(client);
        String asunto = (acepta ? "Propuesta aceptada" : "Propuesta rechazada") + " por " + nombre
                + " (#" + proposal.getProposalId() + ")";
        StringBuilder cuerpo = new StringBuilder()
                .append(nombre).append(acepta ? " aceptó" : " rechazó").append(" una propuesta desde el portal.\n\n")
                .append("Cliente: ").append(nombre).append(" (").append(client.getClientId()).append(")\n")
                .append("Propuesta: #").append(proposal.getProposalId()).append(" - ")
                .append(CommercialFacts.tituloPropuesta(proposal)).append('\n')
                .append("Valor: ").append(proposal.getEstimatedValue() != null
                        ? proposal.getEstimatedValue().setScale(2, RoundingMode.HALF_UP).toPlainString() : "sin valor")
                .append('\n')
                .append("Comentario: ").append(comentario != null ? comentario : "sin comentario");
        try {
            notificationDispatcher.notifyAdmin(asunto, cuerpo.toString());
        } catch (Exception e) {
            log.error("No se pudo avisar a CIMA de la decision de la propuesta {}: {}",
                    proposal.getProposalId(), e.getMessage());
        }
    }

    /** El cliente nunca ve propuestas en diagnostico. */
    static boolean visibleParaElCliente(Proposal p) {
        return p.getStatus() != null && CommercialFacts.ESTADOS_ENVIADA.contains(p.getStatus());
    }

    private static LocalDateTime fechaDeOrden(Proposal p) {
        if (p.getCreatedDate() != null) return p.getCreatedDate().atStartOfDay();
        return p.getCreatedAt();
    }

    static ClientProposalResponse toResponse(Proposal p) {
        BigDecimal valor = p.getEstimatedValue();
        return new ClientProposalResponse(p.getProposalId(), CommercialFacts.tituloPropuesta(p), p.getDescription(),
                valor, p.getStatus() != null ? p.getStatus().name() : null, p.getDocumentUrl(), p.getCreatedDate(),
                p.getResponseDate(), p.getClientComment(), p.getProjectId());
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }
}
