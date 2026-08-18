package com.cimaxis.demo.marketing.service.interactions;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionRequest;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionResponse;
import com.cimaxis.demo.marketing.mapper.interactions.MarketingInteractionMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Gestion de interacciones de marketing.
 */
@Service
public class MarketingInteractionService {

    private final MarketingInteractionRepository interactionRepository;
    private final CampaignRepository campaignRepository;
    private final MarketingInteractionMapper interactionMapper;

    public MarketingInteractionService(MarketingInteractionRepository interactionRepository,
                                       CampaignRepository campaignRepository,
                                       MarketingInteractionMapper interactionMapper) {
        this.interactionRepository = interactionRepository;
        this.campaignRepository = campaignRepository;
        this.interactionMapper = interactionMapper;
    }

    public List<MarketingInteractionResponse> findAll() {
        return interactionMapper.toResponseList(interactionRepository.findAll());
    }

    public MarketingInteractionResponse findById(Integer id) {
        return interactionMapper.toResponse(requireInteraction(id));
    }

    public List<MarketingInteractionResponse> findByCampaign(Integer campaignId) {
        return interactionMapper.toResponseList(interactionRepository.findByCampaignId(campaignId));
    }

    /** Historial de interacciones de un cliente. */
    public List<MarketingInteractionResponse> findByClient(String clientId) {
        return interactionMapper.toResponseList(interactionRepository.findByClientId(clientId));
    }

    public List<MarketingInteractionResponse> findBetween(LocalDateTime from, LocalDateTime to) {
        return interactionMapper.toResponseList(
                interactionRepository.findByContactDateBetween(from, to));
    }

    /** Registro manual de una interaccion por parte del equipo comercial. */
    @Transactional
    public MarketingInteractionResponse registerManual(MarketingInteractionRequest request,
                                                       String loggedBy) {
        if (request.getCampaignId() == null
                || !campaignRepository.existsById(request.getCampaignId())) {
            throw new ResourceNotFoundException("La campana indicada no existe");
        }

        MarketingInteraction interaction = interactionMapper.toEntity(request);
        if (interaction.getInteractionType() == null) {
            interaction.setInteractionType(MarketingInteraction.InteractionType.message);
        }
        if (interaction.getContactDate() == null) {
            interaction.setContactDate(LocalDateTime.now());
        }
        if (interaction.getChannel() == null) {
            interaction.setChannel("manual");
        }
        interaction.setLoggedBy(loggedBy);
        return interactionMapper.toResponse(interactionRepository.save(interaction));
    }

    /**
     * Registrar la respuesta recibida de un cliente.
     */
    @Transactional
    public MarketingInteractionResponse registerResponse(Integer interactionId,
                                                         String response,
                                                         String interactionType) {
        MarketingInteraction interaction = requireInteraction(interactionId);
        interaction.setResponse(response);

        MarketingInteraction.InteractionType tipo = interactionMapper.parseType(interactionType);
        if (tipo != null) {
            interaction.setInteractionType(tipo);
        }
        return interactionMapper.toResponse(interactionRepository.save(interaction));
    }

    @Transactional
    public void delete(Integer id) {
        if (!interactionRepository.existsById(id)) {
            throw ResourceNotFoundException.de("Interaccion", id);
        }
        interactionRepository.deleteById(id);
    }

    private MarketingInteraction requireInteraction(Integer id) {
        return interactionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Interaccion", id));
    }
}
