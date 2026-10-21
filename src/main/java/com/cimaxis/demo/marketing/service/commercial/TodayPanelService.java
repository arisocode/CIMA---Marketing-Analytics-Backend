package com.cimaxis.demo.marketing.service.commercial;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.dto.commercial.TodayResponse;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;

/**
 * Panel Hoy: pendientes comerciales del dia.
 * <ul>
 *   <li>clientsWithoutContact: mas de N dias sin contacto, o nunca contactados
 *       (daysSinceContact null, van primero); luego de mayor a menor.</li>
 *   <li>proposalsWithoutResponse: Sent o In_negotiation abiertas hace mas de N dias
 *       (desde createdDate), de la mas antigua a la mas reciente.</li>
 *   <li>approvedWithoutProject: Approved sin projectId, la aprobada hace mas tiempo primero.</li>
 * </ul>
 * Cada lista se limita a 20 elementos; counts trae el total real.
 */
@Service
public class TodayPanelService {

    public static final int LIMITE = 20;
    public static final int DIAS_SIN_CONTACTO_POR_DEFECTO = 15;
    public static final int DIAS_SIN_RESPUESTA_POR_DEFECTO = 7;

    private final ClientRepository clientRepository;
    private final ProposalRepository proposalRepository;
    private final ClientScoreService scoreService;

    public TodayPanelService(ClientRepository clientRepository,
                             ProposalRepository proposalRepository,
                             ClientScoreService scoreService) {
        this.clientRepository = clientRepository;
        this.proposalRepository = proposalRepository;
        this.scoreService = scoreService;
    }

    public TodayResponse today(Integer daysWithoutContact, Integer daysWithoutResponse) {
        int sinContacto = validar(daysWithoutContact, DIAS_SIN_CONTACTO_POR_DEFECTO, "daysWithoutContact");
        int sinRespuesta = validar(daysWithoutResponse, DIAS_SIN_RESPUESTA_POR_DEFECTO, "daysWithoutResponse");
        LocalDate hoy = scoreService.hoy();
        CommercialFacts hechos = scoreService.hechos();

        List<Client> clientes = nullSafe(clientRepository.findAll());
        Map<String, Client> porId = new HashMap<>();
        clientes.forEach(c -> porId.put(c.getClientId(), c));

        List<TodayResponse.ClientWithoutContact> olvidados = new ArrayList<>();
        for (Client c : clientes) {
            Long dias = InterestScore.diasSinContacto(hechos.of(c.getClientId()).lastContact(), hoy);
            if (dias == null || dias > sinContacto) {
                olvidados.add(new TodayResponse.ClientWithoutContact(
                        c.getClientId(), CommercialFacts.nombre(c), c.getPlan(), dias));
            }
        }
        olvidados.sort(Comparator.comparing(TodayResponse.ClientWithoutContact::daysSinceContact,
                        Comparator.nullsFirst(Comparator.<Long>reverseOrder()))
                .thenComparing(TodayResponse.ClientWithoutContact::name,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        List<Proposal> propuestas = nullSafe(proposalRepository.findAll());

        List<TodayResponse.ProposalWithoutResponse> sinRespuestaLista = new ArrayList<>();
        List<Proposal> aprobadasSinProyecto = new ArrayList<>();
        for (Proposal p : propuestas) {
            if (p.getStatus() == null) continue;
            if (CommercialFacts.ESTADOS_ABIERTA.contains(p.getStatus())) {
                LocalDate desde = p.getCreatedDate() != null ? p.getCreatedDate()
                        : p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null;
                if (desde == null) continue;
                long diasAbierta = ChronoUnit.DAYS.between(desde, hoy);
                if (diasAbierta > sinRespuesta) {
                    Client c = porId.get(p.getClientId());
                    sinRespuestaLista.add(new TodayResponse.ProposalWithoutResponse(p.getProposalId(),
                            CommercialFacts.tituloPropuesta(p), p.getClientId(), CommercialFacts.nombre(c),
                            diasAbierta, p.getEstimatedValue()));
                }
            } else if (p.getStatus() == ProposalStatus.Approved && !CommercialFacts.tieneProyectoVinculado(p)) {
                aprobadasSinProyecto.add(p);
            }
        }
        sinRespuestaLista.sort(Comparator.comparingLong(TodayResponse.ProposalWithoutResponse::daysOpen).reversed());
        aprobadasSinProyecto.sort(Comparator.comparing(Proposal::getResponseDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<TodayResponse.ApprovedWithoutProject> aprobadas = aprobadasSinProyecto.stream()
                .limit(LIMITE)
                .map(p -> new TodayResponse.ApprovedWithoutProject(p.getProposalId(),
                        CommercialFacts.tituloPropuesta(p), p.getClientId(),
                        CommercialFacts.nombre(porId.get(p.getClientId())), p.getEstimatedValue()))
                .toList();

        return new TodayResponse(
                olvidados.stream().limit(LIMITE).toList(),
                sinRespuestaLista.stream().limit(LIMITE).toList(),
                aprobadas,
                new TodayResponse.Counts(olvidados.size(), sinRespuestaLista.size(), aprobadasSinProyecto.size()));
    }

    private static int validar(Integer valor, int porDefecto, String campo) {
        if (valor == null) return porDefecto;
        if (valor < 0) {
            throw new IllegalArgumentException("El parámetro " + campo + " no puede ser negativo");
        }
        return valor;
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }
}
