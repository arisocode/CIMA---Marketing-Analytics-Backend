package com.cimaxis.demo.integration.crm.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cimaxis.demo.config.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;


@Service
public class ClientOverviewService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final CampaignRepository campaignRepository;
    private final ProposalRepository proposalRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final CrmIntegrationService crmIntegrationService;

    public ClientOverviewService(ClientRepository clientRepository,
                                 ProjectRepository projectRepository,
                                 CampaignRepository campaignRepository,
                                 ProposalRepository proposalRepository,
                                 MarketingInteractionRepository interactionRepository,
                                 WorkflowExecutionRepository executionRepository,
                                 CrmIntegrationService crmIntegrationService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.campaignRepository = campaignRepository;
        this.proposalRepository = proposalRepository;
        this.interactionRepository = interactionRepository;
        this.executionRepository = executionRepository;
        this.crmIntegrationService = crmIntegrationService;
    }

    public Map<String, Object> getOverview(String clientId, String bearerToken) {

        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Cliente no encontrado: " + clientId);
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("clientId", clientId);

        clientRepository.findById(clientId).ifPresent(client ->
                overview.put("plan", client.getPlan() != null ? client.getPlan().name() : null));

        // Datos de contacto desde el CRM base (RF-02)
        if (bearerToken != null) {
            try {
                crmIntegrationService.getClients(bearerToken).stream()
                        .filter(c -> clientId.equals(crmIntegrationService.extractClientId(c)))
                        .findFirst()
                        .ifPresent(c -> {
                            overview.put("name", crmIntegrationService.extractClientName(c));
                            overview.put("email", crmIntegrationService.extractClientEmail(c));
                            overview.put("crmData", c);
                        });
            } catch (Exception e) {
                overview.put("crmWarning", "No se pudo consultar el CRM: " + e.getMessage());
            }
        }

        List<Project> proyectos = projectRepository.findByClientId(clientId);

        overview.put("projects", proyectos);
        overview.put("projectCount", proyectos.size());
        overview.put("campaigns", campaignRepository.findByClientId(clientId));
        overview.put("proposals", proposalRepository.findByClientId(clientId));
        overview.put("interactions", interactionRepository.findByClientId(clientId));
        overview.put("workflowExecutions", executionRepository.findByClientId(clientId));

        return overview;
    }

    public List<Project> getProjectsByClient(String clientId) {
        return projectRepository.findByClientId(clientId);
    }
}
