package com.cimaxis.demo.marketing.service.portal;

import java.time.LocalDateTime;
import java.time.ZoneId;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;

/**
 * Utilidades compartidas del portal del cliente y de la conversacion comercial.
 */
public final class PortalSupport {

    public static final ZoneId ZONA_CIMA = ZoneId.of("America/Bogota");
    public static final String CANAL_PORTAL = "portal";
    public static final String CUENTA_NO_SINCRONIZADA = "Tu cuenta aún no está sincronizada con marketing";

    private PortalSupport() {
    }

    /**
     * Cliente del usuario autenticado. El clientId de schema_marketing.clients es el
     * subject del usuario en crm-auth, asi que clientId = X-User-Sub.
     */
    public static Client clienteAutenticado(ClientRepository clientRepository, String sub) {
        if (sub == null || sub.isBlank()) {
            throw new ResourceNotFoundException(CUENTA_NO_SINCRONIZADA);
        }
        return clientRepository.findById(sub.trim())
                .orElseThrow(() -> new ResourceNotFoundException(CUENTA_NO_SINCRONIZADA));
    }

    /** Cliente por id para las vistas de CIMA (404 si no existe). */
    public static Client cliente(ClientRepository clientRepository, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw ResourceNotFoundException.de("Cliente", clientId);
        }
        return clientRepository.findById(clientId.trim())
                .orElseThrow(() -> ResourceNotFoundException.de("Cliente", clientId));
    }

    /** Nombre visible del cliente; "Cliente" si no tiene nombre ni correo. */
    public static String nombre(Client client) {
        String nombre = CommercialFacts.nombre(client);
        return nombre != null ? nombre : "Cliente";
    }

    /** Interaccion sin campaña, registrada desde el portal. */
    public static MarketingInteraction interaccionDelPortal(String clientId,
                                                           MarketingInteraction.InteractionType tipo,
                                                           String response,
                                                           String loggedBy,
                                                           LocalDateTime fecha) {
        return MarketingInteraction.builder()
                .campaignId(null)
                .clientId(clientId)
                .loggedBy(loggedBy)
                .contactDate(fecha)
                .interactionType(tipo)
                .channel(CANAL_PORTAL)
                .response(response)
                .build();
    }

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
