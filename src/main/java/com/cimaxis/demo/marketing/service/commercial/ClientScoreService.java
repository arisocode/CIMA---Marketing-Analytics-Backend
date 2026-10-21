package com.cimaxis.demo.marketing.service.commercial;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.dto.commercial.MarketingClientResponse;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Carga los hechos comerciales de todos los clientes y calcula el puntaje de
 * interes (InterestScore) con la fecha de hoy en America/Bogota.
 */
@Service
public class ClientScoreService {

    public static final ZoneId ZONA_CIMA = ZoneId.of("America/Bogota");

    private final MarketingInteractionRepository interactionRepository;
    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    @Autowired
    public ClientScoreService(MarketingInteractionRepository interactionRepository,
                              ProposalRepository proposalRepository,
                              ProjectRepository projectRepository) {
        this(interactionRepository, proposalRepository, projectRepository, Clock.system(ZONA_CIMA));
    }

    ClientScoreService(MarketingInteractionRepository interactionRepository,
                       ProposalRepository proposalRepository,
                       ProjectRepository projectRepository,
                       Clock clock) {
        this.interactionRepository = interactionRepository;
        this.proposalRepository = proposalRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    /** Hechos de todos los clientes, con todo el historico. */
    public CommercialFacts hechos() {
        return CommercialFacts.from(interactionRepository.findAll(),
                proposalRepository.findAll(),
                projectRepository.findAll());
    }

    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA_CIMA));
    }

    public int score(Client client, CommercialFacts hechos) {
        return InterestScore.calcular(client, hechos.of(client != null ? client.getClientId() : null), hoy());
    }

    /** Listado de clientes de marketing con su puntaje. */
    public List<MarketingClientResponse> conPuntaje(List<Client> clientes) {
        if (clientes == null || clientes.isEmpty()) return List.of();
        CommercialFacts hechos = hechos();
        LocalDate hoy = hoy();
        return clientes.stream()
                .map(c -> MarketingClientResponse.of(c, InterestScore.calcular(c, hechos.of(c.getClientId()), hoy)))
                .toList();
    }
}
