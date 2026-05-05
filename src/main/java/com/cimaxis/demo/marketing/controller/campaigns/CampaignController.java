package com.cimaxis.demo.marketing.controller.campaigns;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/marketing/campaigns")
public class CampaignController {

    private final CampaignRepository campaignRepository;

    public CampaignController(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    // Obtengo todas las campañas
    @GetMapping
    public ResponseEntity<List<Campaign>> getAll() {
        return ResponseEntity.ok(campaignRepository.findAll());
    }

    // Obtengo campaña por ID
    @GetMapping("/{id}")
    public ResponseEntity<Campaign> getById(@PathVariable Integer id) {
        return campaignRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Obtengo campañas por cliente
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<Campaign>> getByClient(@PathVariable Integer clientId) {
        return ResponseEntity.ok(campaignRepository.findByClientId(clientId));
    }

    // Para crear campaña
    @PostMapping
    public ResponseEntity<Campaign> create(@RequestBody Campaign campaign,
                                            HttpServletRequest request) {

        System.out.println("Creando campaña: " + campaign.getCampaignName() + " para cliente ID: " + campaign.getClientId());
        // Tomar el userId del contexto de seguridad
        Integer userId = (Integer) request.getAttribute("userId"); 
        if (userId != null) campaign.setCreatedBy(userId);

        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        if (campaign.getStatus() == null) {
            campaign.setStatus(Campaign.CampaignStatus.Draft);
        }
        return ResponseEntity.ok(campaignRepository.save(campaign));
    }

    // Actualizar campaña
    @PutMapping("/{id}")
    public ResponseEntity<Campaign> update(@PathVariable Integer id,
                                            @RequestBody Campaign updated) {
        return campaignRepository.findById(id).map(existing -> {
            existing.setCampaignName(updated.getCampaignName());
            existing.setCampaignType(updated.getCampaignType());
            existing.setStatus(updated.getStatus());
            existing.setStartDate(updated.getStartDate());
            existing.setEndDate(updated.getEndDate());
            existing.setPlatforms(updated.getPlatforms());
            existing.setObjective(updated.getObjective());
            existing.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(campaignRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Eliminar campaña
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!campaignRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        campaignRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
