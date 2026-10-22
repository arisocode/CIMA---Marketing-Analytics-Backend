package com.cimaxis.demo.marketing.service.campaigns;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.segments.MarketingSegmentRepository;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;
import com.cimaxis.demo.marketing.service.segmentation.ClientSegmentationService;
import com.cimaxis.demo.marketing.service.segments.SegmentCriteriaCodec;

/**
 * Audiencia de una campaña: el cliente (CLIENT), los clientes que hoy cumplen el
 * segmento guardado (SEGMENT) o todos los clientes (ALL). La lista se calcula
 * cada vez; no se guarda.
 */
@Service
public class CampaignAudienceService {

    private final ClientRepository clientRepository;
    private final MarketingSegmentRepository segmentRepository;
    private final ClientSegmentationService segmentationService;
    private final CampaignRepository campaignRepository;

    public CampaignAudienceService(ClientRepository clientRepository,
                                   MarketingSegmentRepository segmentRepository,
                                   ClientSegmentationService segmentationService,
                                   CampaignRepository campaignRepository) {
        this.clientRepository = clientRepository;
        this.segmentRepository = segmentRepository;
        this.segmentationService = segmentationService;
        this.campaignRepository = campaignRepository;
    }

    /** clientIds de la audiencia actual de la campaña. */
    public List<String> resolve(Campaign campaign) {
        return resolve(campaign, new HashMap<>());
    }

    /** Igual que resolve, reutilizando los segmentos ya calculados (cache por segmentId). */
    public List<String> resolve(Campaign campaign, Map<UUID, List<String>> cache) {
        if (campaign == null) return List.of();
        return switch (campaign.audienceOrDefault()) {
            case CLIENT -> campaign.getClientId() != null && !campaign.getClientId().isBlank()
                    ? List.of(campaign.getClientId()) : List.of();
            case ALL -> clientRepository.findAll().stream().map(Client::getClientId).toList();
            case SEGMENT -> campaign.getSegmentId() == null ? List.of()
                    : cache.computeIfAbsent(campaign.getSegmentId(), this::clientesDelSegmento);
        };
    }

    public List<String> clientesDelSegmento(UUID segmentId) {
        return segmentRepository.findById(segmentId)
                .map(s -> segmentationService.segmentIds(SegmentCriteriaCodec.fromJson(s.getCriteria())))
                .orElse(List.of());
    }

    public Optional<MarketingSegment> segmento(Campaign campaign) {
        if (campaign == null || campaign.getSegmentId() == null) return Optional.empty();
        return segmentRepository.findById(campaign.getSegmentId());
    }

    /**
     * Texto de la audiencia para reportes y listados: nombre del cliente,
     * "Segmento: <nombre>" o "Todos los clientes".
     */
    public String describe(Campaign campaign, Map<String, Client> clientes) {
        if (campaign == null) return "";
        return switch (campaign.audienceOrDefault()) {
            case ALL -> "Todos los clientes";
            case SEGMENT -> "Segmento: " + segmento(campaign).map(MarketingSegment::getName).orElse("(eliminado)");
            case CLIENT -> {
                if (campaign.getClientId() == null) yield "Sin cliente";
                Client c = clientes != null ? clientes.get(campaign.getClientId()) : null;
                if (c == null) c = clientRepository.findById(campaign.getClientId()).orElse(null);
                String nombre = CommercialFacts.nombre(c);
                yield nombre != null ? nombre : "Cliente no sincronizado";
            }
        };
    }

    /** Campañas cuya audiencia actual incluye al cliente (CLIENT propio, ALL o un segmento que lo contiene). */
    public List<Campaign> campaignsForClient(String clientId) {
        if (clientId == null) return List.of();
        Set<Campaign> resultado = new LinkedHashSet<>();
        Map<UUID, List<String>> cache = new HashMap<>();
        for (Campaign c : campaignRepository.findAll()) {
            boolean incluye = switch (c.audienceOrDefault()) {
                case CLIENT -> clientId.equals(c.getClientId());
                case ALL -> true;
                case SEGMENT -> resolve(c, cache).contains(clientId);
            };
            if (incluye) resultado.add(c);
        }
        return new ArrayList<>(resultado);
    }

    /** La campaña puede actuar hoy: estado Active y hoy entre sus fechas (fin opcional). */
    public static boolean vigente(Campaign c, LocalDate hoy) {
        return c != null
                && c.getStatus() == Campaign.CampaignStatus.Active
                && c.getStartDate() != null
                && !hoy.isBefore(c.getStartDate())
                && (c.getEndDate() == null || !hoy.isAfter(c.getEndDate()));
    }
}
