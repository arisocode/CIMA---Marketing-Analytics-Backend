package com.cimaxis.demo.marketing.service.campaigns;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;
import com.cimaxis.demo.marketing.service.segments.SavedSegmentService;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignRequest;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignResponse;
import com.cimaxis.demo.marketing.mapper.campaigns.CampaignMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;

/**
 * Logica de negocio de campanas.
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;
    private final CampaignAudienceService audienceService;
    private final SavedSegmentService segmentService;

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignMapper campaignMapper,
                           CampaignAudienceService audienceService,
                           SavedSegmentService segmentService) {
        this.campaignRepository = campaignRepository;
        this.campaignMapper = campaignMapper;
        this.audienceService = audienceService;
        this.segmentService = segmentService;
    }

    public List<CampaignResponse> findAll() {
        return conAudiencia(campaignRepository.findAll());
    }

    public CampaignResponse findById(Integer id) {
        return conAudiencia(List.of(requireCampaign(id))).get(0);
    }

    /**
     * Campañas cuya audiencia actual incluye al cliente: las suyas (CLIENT), las de
     * todos los clientes (ALL) y las de segmentos que hoy lo contienen.
     */
    public List<CampaignResponse> findByClient(String clientId) {
        return conAudiencia(audienceService.campaignsForClient(clientId));
    }

    /**
     * El autor sale del usuario autenticado, nunca del cuerpo de la peticion.
     */
    @Transactional
    public CampaignResponse create(CampaignRequest request, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResourceNotFoundException(
                    "No se pudo determinar el usuario autenticado para registrar la campana");
        }

        Campaign campaign = campaignMapper.toEntity(request);
        aplicarAudiencia(request, campaign, Campaign.AudienceType.CLIENT);
        campaign.setCreatedBy(userId);
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        if (campaign.getStatus() == null) {
            campaign.setStatus(Campaign.CampaignStatus.Draft);
        }
        return conAudiencia(List.of(campaignRepository.save(campaign))).get(0);
    }

    /** Sin audienceType en el cuerpo se conserva la audiencia actual. */
    @Transactional
    public CampaignResponse update(Integer id, CampaignRequest request) {
        Campaign existing = requireCampaign(id);
        campaignMapper.aplicarCambios(request, existing);
        if (request.getAudienceType() != null && !request.getAudienceType().isBlank()) {
            aplicarAudiencia(request, existing, existing.audienceOrDefault());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return conAudiencia(List.of(campaignRepository.save(existing))).get(0);
    }

    @Transactional
    public void delete(Integer id) {
        if (!campaignRepository.existsById(id)) {
            throw ResourceNotFoundException.de("Campana", id);
        }
        campaignRepository.deleteById(id);
    }

    /**
     * Valida y fija la audiencia: CLIENT exige el clientId (como antes, sin exigir que ya
     * este sincronizado); SEGMENT un
     * segmento guardado activo; ALL no usa ni cliente ni segmento.
     */
    private void aplicarAudiencia(CampaignRequest request, Campaign destino, Campaign.AudienceType porDefecto) {
        Campaign.AudienceType tipo = campaignMapper.parseAudience(request.getAudienceType());
        if (tipo == null) tipo = porDefecto;
        switch (tipo) {
            case CLIENT -> {
                String clientId = request.getClientId() == null ? null : request.getClientId().trim();
                if (clientId == null || clientId.isEmpty()) {
                    throw new IllegalArgumentException("El cliente es obligatorio cuando la audiencia es CLIENT");
                }
                destino.setClientId(clientId);
                destino.setSegmentId(null);
            }
            case SEGMENT -> {
                if (request.getSegmentId() == null) {
                    throw new IllegalArgumentException("El segmento es obligatorio cuando la audiencia es SEGMENT");
                }
                segmentService.requireActive(request.getSegmentId());
                destino.setSegmentId(request.getSegmentId());
                destino.setClientId(null);
            }
            case ALL -> {
                destino.setClientId(null);
                destino.setSegmentId(null);
            }
        }
        destino.setAudienceType(tipo);
    }

    /** Respuesta con nombre del segmento y tamaño actual de la audiencia. */
    private List<CampaignResponse> conAudiencia(List<Campaign> campanas) {
        Map<UUID, List<String>> cache = new HashMap<>();
        List<CampaignResponse> respuestas = new ArrayList<>();
        for (Campaign c : campanas) {
            CampaignResponse r = campaignMapper.toResponse(c);
            r.setAudienceSize(audienceService.resolve(c, cache).size());
            if (c.audienceOrDefault() == Campaign.AudienceType.SEGMENT) {
                r.setSegmentName(audienceService.segmento(c).map(MarketingSegment::getName).orElse(null));
            }
            respuestas.add(r);
        }
        return respuestas;
    }

    private Campaign requireCampaign(Integer id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Campana", id));
    }
}
