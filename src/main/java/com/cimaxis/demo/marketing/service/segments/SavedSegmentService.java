package com.cimaxis.demo.marketing.service.segments;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentRequest;
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentResponse;
import com.cimaxis.demo.marketing.dto.segments.SegmentClientResponse;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.segments.MarketingSegmentRepository;
import com.cimaxis.demo.marketing.service.commercial.ClientScoreService;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;
import com.cimaxis.demo.marketing.service.commercial.InterestScore;
import com.cimaxis.demo.marketing.service.segmentation.ClientSegmentationService;
import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/**
 * Segmentos guardados: un nombre para un SegmentCriteria. La lista de clientes
 * no se guarda; se recalcula con ClientSegmentationService cada vez (la misma
 * logica de /segments/preview). Un segmento no se borra: se desactiva, y no se
 * puede desactivar mientras lo use una campaña activa.
 */
@Service
public class SavedSegmentService {

    private static final int LONGITUD_MAXIMA_NOMBRE = 120;

    private final MarketingSegmentRepository segmentRepository;
    private final ClientSegmentationService segmentationService;
    private final CampaignRepository campaignRepository;
    private final ClientRepository clientRepository;
    private final ClientScoreService scoreService;

    public SavedSegmentService(MarketingSegmentRepository segmentRepository,
                               ClientSegmentationService segmentationService,
                               CampaignRepository campaignRepository,
                               ClientRepository clientRepository,
                               ClientScoreService scoreService) {
        this.segmentRepository = segmentRepository;
        this.segmentationService = segmentationService;
        this.campaignRepository = campaignRepository;
        this.clientRepository = clientRepository;
        this.scoreService = scoreService;
    }

    public List<SavedSegmentResponse> list() {
        return segmentRepository.findAllByOrderByNameAsc().stream()
                .sorted(Comparator.comparing((MarketingSegment s) -> !Boolean.TRUE.equals(s.getActive())))
                .map(this::toResponse)
                .toList();
    }

    public SavedSegmentResponse findById(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public SavedSegmentResponse create(SavedSegmentRequest request) {
        MarketingSegment segmento = MarketingSegment.builder()
                .segmentId(UUID.randomUUID())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        aplicar(request, segmento);
        return toResponse(segmentRepository.save(segmento));
    }

    @Transactional
    public SavedSegmentResponse update(UUID id, SavedSegmentRequest request) {
        MarketingSegment segmento = require(id);
        aplicar(request, segmento);
        return toResponse(segmentRepository.save(segmento));
    }

    @Transactional
    public SavedSegmentResponse setActive(UUID id, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("El campo active es obligatorio");
        }
        MarketingSegment segmento = require(id);
        if (!active) {
            List<Campaign> activas = campaignRepository.findBySegmentIdAndStatus(id, Campaign.CampaignStatus.Active);
            if (activas != null && !activas.isEmpty()) {
                throw new IllegalStateException("No se puede desactivar el segmento '" + segmento.getName()
                        + "': lo usa la campaña activa '" + activas.get(0).getCampaignName()
                        + "'. Pausa la campaña o cambia su audiencia primero.");
            }
        }
        segmento.setActive(active);
        return toResponse(segmentRepository.save(segmento));
    }

    /** Clientes que hoy forman el segmento, de mayor a menor puntaje de interes. */
    public List<SegmentClientResponse> clients(UUID id) {
        MarketingSegment segmento = require(id);
        List<String> ids = segmentationService.segmentIds(SegmentCriteriaCodec.fromJson(segmento.getCriteria()));
        Map<String, Client> clientes = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Client c : clientRepository.findAllById(ids)) clientes.put(c.getClientId(), c);
        }
        CommercialFacts hechos = scoreService.hechos();
        LocalDate hoy = scoreService.hoy();
        return ids.stream()
                .map(clientId -> {
                    Client c = clientes.get(clientId);
                    CommercialFacts.ClientFacts h = hechos.of(clientId);
                    return new SegmentClientResponse(clientId, CommercialFacts.nombre(c),
                            c != null ? c.getPlan() : null, h.lastContact(), InterestScore.calcular(c, h, hoy));
                })
                .sorted(Comparator.comparingInt(SegmentClientResponse::score).reversed()
                        .thenComparing(SegmentClientResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /** Segmento activo por id; lo usan las campañas con audiencia SEGMENT. */
    public MarketingSegment requireActive(UUID id) {
        MarketingSegment segmento = require(id);
        if (!Boolean.TRUE.equals(segmento.getActive())) {
            throw new IllegalArgumentException("El segmento '" + segmento.getName() + "' está inactivo");
        }
        return segmento;
    }

    // ------------------------------------------------------------------

    private void aplicar(SavedSegmentRequest request, MarketingSegment destino) {
        if (request == null) {
            throw new IllegalArgumentException("Los datos del segmento son obligatorios");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("El nombre del segmento es obligatorio");
        }
        String nombre = request.name().trim();
        if (nombre.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new IllegalArgumentException("El nombre del segmento admite máximo " + LONGITUD_MAXIMA_NOMBRE + " caracteres");
        }
        segmentRepository.findByNameIgnoreCase(nombre)
                .filter(otro -> !otro.getSegmentId().equals(destino.getSegmentId()))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("Ya existe un segmento llamado " + otro.getName());
                });
        SegmentCriteria criterio = request.criteria() != null ? request.criteria() : new SegmentCriteria();
        if (criterio.getMinDaysWithoutContact() != null && criterio.getMinDaysWithoutContact() < 0) {
            throw new IllegalArgumentException("minDaysWithoutContact no puede ser negativo");
        }
        // Valida los planes contra el catalogo (lanza 400 si alguno no existe).
        segmentationService.segmentIds(criterio);

        destino.setName(nombre);
        destino.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        destino.setCriteria(SegmentCriteriaCodec.toJson(criterio));
    }

    private MarketingSegment require(UUID id) {
        if (id == null) throw ResourceNotFoundException.de("Segmento", null);
        return segmentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Segmento", id));
    }

    private SavedSegmentResponse toResponse(MarketingSegment s) {
        SegmentCriteria criterio = SegmentCriteriaCodec.fromJson(s.getCriteria());
        long total;
        try {
            total = segmentationService.segmentIds(criterio).size();
        } catch (IllegalArgumentException e) {
            // p. ej. un plan del criterio ya no existe en el catalogo
            total = 0;
        }
        return new SavedSegmentResponse(s.getSegmentId(), s.getName(), s.getDescription(), criterio,
                s.getActive(), total);
    }
}
