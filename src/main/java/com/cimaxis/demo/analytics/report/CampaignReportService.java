package com.cimaxis.demo.analytics.report;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.service.campaigns.CampaignAudienceService;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;

/**
 * Datos del reporte de campañas: una fila por campaña con su audiencia (nombre del
 * cliente, "Segmento: <nombre>" o "Todos los clientes"),
 * ordenadas por estado (Activa, Pausada, Borrador, Completada, Cancelada) y luego
 * por fecha de inicio.
 */
@Service
public class CampaignReportService {

    private static final List<Campaign.CampaignStatus> ORDEN_ESTADOS = List.of(
            Campaign.CampaignStatus.Active, Campaign.CampaignStatus.Paused, Campaign.CampaignStatus.Draft,
            Campaign.CampaignStatus.Completed, Campaign.CampaignStatus.Cancelled);

    private final CampaignRepository campaignRepository;
    private final ClientRepository clientRepository;
    private final CampaignAudienceService audienceService;

    public CampaignReportService(CampaignRepository campaignRepository, ClientRepository clientRepository,
                                 CampaignAudienceService audienceService) {
        this.campaignRepository = campaignRepository;
        this.clientRepository = clientRepository;
        this.audienceService = audienceService;
    }

    public List<CampaignReportRow> rows() {
        List<Campaign> campanas = campaignRepository.findAll();
        if (campanas == null || campanas.isEmpty()) return List.of();

        Set<String> ids = new HashSet<>();
        campanas.forEach(c -> { if (c.getClientId() != null) ids.add(c.getClientId()); });
        Map<String, Client> clientes = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Client> encontrados = clientRepository.findAllById(ids);
            if (encontrados != null) encontrados.forEach(c -> clientes.put(c.getClientId(), c));
        }

        return campanas.stream()
                .sorted(Comparator.comparingInt((Campaign c) -> ordenEstado(c.getStatus()))
                        .thenComparing(Campaign::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Campaign::getCampaignName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(c -> new CampaignReportRow(c.getCampaignName(), audiencia(c, clientes),
                        c.getCampaignType() != null ? c.getCampaignType().name() : null,
                        c.getStatus() != null ? c.getStatus().name() : null,
                        c.getStartDate(), c.getEndDate(), c.getPlatforms(), c.getObjective()))
                .toList();
    }

    static int ordenEstado(Campaign.CampaignStatus estado) {
        int i = estado == null ? -1 : ORDEN_ESTADOS.indexOf(estado);
        return i < 0 ? ORDEN_ESTADOS.size() : i;
    }

    /** Columna Cliente: nombre del cliente, "Segmento: <nombre>" o "Todos los clientes". */
    private String audiencia(Campaign c, Map<String, Client> clientes) {
        if (c.audienceOrDefault() != Campaign.AudienceType.CLIENT) {
            return audienceService.describe(c, clientes);
        }
        return nombreCliente(clientes.get(c.getClientId()), c.getClientId());
    }

    private static String nombreCliente(Client client, String clientId) {
        String nombre = CommercialFacts.nombre(client);
        if (nombre != null) return nombre;
        return clientId == null ? "Sin cliente" : "Cliente no sincronizado";
    }
}
