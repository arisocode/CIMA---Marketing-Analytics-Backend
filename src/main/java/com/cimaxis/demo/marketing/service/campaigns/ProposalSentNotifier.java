package com.cimaxis.demo.marketing.service.campaigns;

import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.EmailService;
import com.cimaxis.demo.marketing.service.portal.PortalSupport;

/**
 * Avisa al cliente por correo cuando CIMA le envia una propuesta (estado Sent).
 * Usa EmailService, asi que respeta el cupo diario; un fallo nunca interrumpe
 * el cambio de estado.
 */
@Component
public class ProposalSentNotifier {

    private static final Logger log = LoggerFactory.getLogger(ProposalSentNotifier.class);

    public static final String ASUNTO = "Tienes una nueva propuesta de CIMA";

    private final EmailService emailService;
    private final ClientRepository clientRepository;

    public ProposalSentNotifier(EmailService emailService, ClientRepository clientRepository) {
        this.emailService = emailService;
        this.clientRepository = clientRepository;
    }

    public DispatchResult notifySent(Proposal proposal) {
        try {
            Client client = proposal.getClientId() == null ? null
                    : clientRepository.findById(proposal.getClientId()).orElse(null);
            if (client == null) {
                return DispatchResult.deliveryFailed("email", "El cliente de la propuesta no existe");
            }
            StringBuilder cuerpo = new StringBuilder()
                    .append("Hola ").append(PortalSupport.nombre(client)).append(":\n\n")
                    .append("CIMA te envió una nueva propuesta: ").append(CommercialFacts.tituloPropuesta(proposal))
                    .append('.');
            if (proposal.getEstimatedValue() != null) {
                cuerpo.append("\nValor estimado: ")
                      .append(proposal.getEstimatedValue().setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
            cuerpo.append("\n\nIngresa al portal de CIMAxis para revisarla y aceptarla o rechazarla.");
            return emailService.send(client.getContactInfo(), ASUNTO, cuerpo.toString(), EmailService.ORIGEN_PROPUESTA);
        } catch (Exception e) {
            log.error("No se pudo avisar al cliente de la propuesta {}: {}", proposal.getProposalId(), e.getMessage());
            return DispatchResult.deliveryFailed("email", e.getMessage());
        }
    }
}
