package com.cimaxis.demo.marketing.service.segmentation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Segmentacion de clientes para campanas.
 *
 */
@Service
@RequiredArgsConstructor
public class ClientSegmentationService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final ProposalRepository proposalRepository;

    public List<Client> segment(SegmentCriteria criteria) {
        List<Client> resultado = new ArrayList<>();

        for (Client client : clientRepository.findAll()) {
            if (!cumplePlan(client, criteria)) continue;
            if (!cumpleProyectos(client, criteria)) continue;
            if (!cumpleInteracciones(client, criteria)) continue;
            if (!cumpleSinContacto(client, criteria)) continue;
            if (!cumplePropuestas(client, criteria)) continue;
            resultado.add(client);
        }
        return resultado;
    }

    public List<String> segmentIds(SegmentCriteria criteria) {
        return segment(criteria).stream().map(Client::getClientId).toList();
    }

    private boolean cumplePlan(Client client, SegmentCriteria criteria) {
        if (criteria.getPlans() == null || criteria.getPlans().isEmpty()) return true;
        if (client.getPlan() == null) return false;
        return criteria.getPlans().stream()
                .anyMatch(p -> p.equalsIgnoreCase(client.getPlan().name()));
    }

    private boolean cumpleProyectos(Client client, SegmentCriteria criteria) {
        if (criteria.getHasProjects() == null) return true;
        boolean tiene = projectRepository.countByClientId(client.getClientId()) > 0;
        return tiene == criteria.getHasProjects();
    }

    private boolean cumpleInteracciones(Client client, SegmentCriteria criteria) {
        if (criteria.getHasInteractions() == null) return true;
        boolean tiene = !interactionRepository.findByClientId(client.getClientId()).isEmpty();
        return tiene == criteria.getHasInteractions();
    }

    /**
     * Cliente no contactado
     */
    private boolean cumpleSinContacto(Client client, SegmentCriteria criteria) {
        if (criteria.getMinDaysWithoutContact() == null) return true;
        LocalDateTime limite = LocalDateTime.now().minusDays(criteria.getMinDaysWithoutContact());
        MarketingInteraction ultima = interactionRepository
                .findTopByClientIdOrderByContactDateDesc(client.getClientId());
        return ultima == null || ultima.getContactDate() == null
                || ultima.getContactDate().isBefore(limite);
    }

    private boolean cumplePropuestas(Client client, SegmentCriteria criteria) {
        if (criteria.getProposalStatuses() == null || criteria.getProposalStatuses().isEmpty()) {
            return true;
        }
        List<Proposal> propuestas = proposalRepository.findByClientId(client.getClientId());
        return propuestas.stream().anyMatch(p -> p.getStatus() != null
                && criteria.getProposalStatuses().stream()
                .anyMatch(s -> normalizar(s).equals(normalizar(p.getStatus().name()))));
    }

    /** Tolera "In negotiation", "in_negotiation" y "IN_NEGOTIATION" por igual. */
    private String normalizar(String valor) {
        return valor == null ? "" : valor.toLowerCase(Locale.ROOT).replace(" ", "_").trim();
    }
}
