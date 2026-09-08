package com.cimaxis.demo.marketing.service.campaigns;

import java.time.LocalDateTime;
import java.util.List;

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

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignMapper campaignMapper) {
        this.campaignRepository = campaignRepository;
        this.campaignMapper = campaignMapper;
    }

    public List<CampaignResponse> findAll() {
        return campaignMapper.toResponseList(campaignRepository.findAll());
    }

    public CampaignResponse findById(Integer id) {
        return campaignMapper.toResponse(requireCampaign(id));
    }

    public List<CampaignResponse> findByClient(String clientId) {
        return campaignMapper.toResponseList(campaignRepository.findByClientId(clientId));
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
        campaign.setCreatedBy(userId);
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        if (campaign.getStatus() == null) {
            campaign.setStatus(Campaign.CampaignStatus.Draft);
        }
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse update(Integer id, CampaignRequest request) {
        Campaign existing = requireCampaign(id);
        campaignMapper.aplicarCambios(request, existing);
        existing.setUpdatedAt(LocalDateTime.now());
        return campaignMapper.toResponse(campaignRepository.save(existing));
    }

    @Transactional
    public void delete(Integer id) {
        if (!campaignRepository.existsById(id)) {
            throw ResourceNotFoundException.de("Campana", id);
        }
        campaignRepository.deleteById(id);
    }

    private Campaign requireCampaign(Integer id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.de("Campana", id));
    }
}
