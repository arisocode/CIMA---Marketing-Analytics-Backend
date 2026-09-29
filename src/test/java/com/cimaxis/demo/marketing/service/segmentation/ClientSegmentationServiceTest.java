package com.cimaxis.demo.marketing.service.segmentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Segmentacion de clientes (RF-11). Los criterios se combinan con conjuncion
 * logica: un cliente entra al segmento solo si cumple todos los criterios dados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientSegmentationServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private ProposalRepository proposalRepository;

    @InjectMocks private ClientSegmentationService service;

    private final Client oro = cliente("c-oro", Client.Plan.Oro);
    private final Client diamante = cliente("c-diamante", Client.Plan.Diamante);
    private final Client sinPlan = cliente("c-sin-plan", null);

    @BeforeEach
    void setUp() {
        when(clientRepository.findAll()).thenReturn(List.of(oro, diamante, sinPlan));
        // Por defecto nadie tiene proyectos, interacciones ni propuestas.
        when(projectRepository.countByClientId(anyString())).thenReturn(0L);
        when(interactionRepository.findByClientId(anyString())).thenReturn(List.of());
        when(interactionRepository.findTopByClientIdOrderByContactDateDesc(anyString())).thenReturn(null);
        when(proposalRepository.findByClientId(anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Sin criterios, el segmento incluye a todos los clientes")
    void sinCriteriosIncluyeATodos() {
        assertThat(ids(new SegmentCriteria())).containsExactly("c-oro", "c-diamante", "c-sin-plan");
    }

    @Test
    @DisplayName("El filtro por plan no distingue mayusculas")
    void filtroPorPlanSinDistinguirMayusculas() {
        SegmentCriteria criterio = SegmentCriteria.builder().plans(List.of("oro")).build();

        assertThat(ids(criterio)).containsExactly("c-oro");
    }

    @Test
    @DisplayName("Con varios planes se incluyen los clientes de cualquiera de ellos")
    void variosPlanesSeUnen() {
        SegmentCriteria criterio = SegmentCriteria.builder().plans(List.of("Oro", "Diamante")).build();

        assertThat(ids(criterio)).containsExactly("c-oro", "c-diamante");
    }

    @Test
    @DisplayName("Un cliente sin plan asignado queda fuera cuando se filtra por plan")
    void clienteSinPlanQuedaFuera() {
        SegmentCriteria criterio = SegmentCriteria.builder().plans(List.of("Platinum")).build();

        assertThat(ids(criterio)).isEmpty();
    }

    @Test
    @DisplayName("hasProjects=true deja solo a los clientes con al menos un proyecto")
    void soloClientesConProyectos() {
        when(projectRepository.countByClientId("c-diamante")).thenReturn(2L);
        SegmentCriteria criterio = SegmentCriteria.builder().hasProjects(true).build();

        assertThat(ids(criterio)).containsExactly("c-diamante");
    }

    @Test
    @DisplayName("hasProjects=false deja solo a los clientes sin proyectos")
    void soloClientesSinProyectos() {
        when(projectRepository.countByClientId("c-diamante")).thenReturn(2L);
        SegmentCriteria criterio = SegmentCriteria.builder().hasProjects(false).build();

        assertThat(ids(criterio)).containsExactly("c-oro", "c-sin-plan");
    }

    @Test
    @DisplayName("hasInteractions=true deja solo a los clientes ya contactados")
    void soloClientesYaContactados() {
        when(interactionRepository.findByClientId("c-oro"))
                .thenReturn(List.of(interaccion("c-oro", LocalDateTime.now())));
        SegmentCriteria criterio = SegmentCriteria.builder().hasInteractions(true).build();

        assertThat(ids(criterio)).containsExactly("c-oro");
    }

    @Test
    @DisplayName("Un cliente que nunca ha sido contactado cumple el criterio de dias sin contacto")
    void nuncaContactadoCumpleDiasSinContacto() {
        SegmentCriteria criterio = SegmentCriteria.builder().minDaysWithoutContact(15).build();

        assertThat(ids(criterio)).containsExactly("c-oro", "c-diamante", "c-sin-plan");
    }

    @Test
    @DisplayName("Con 15 dias sin contacto entra quien lleva 30 dias y sale quien lleva 3")
    void diasSinContactoComparaConLaUltimaInteraccion() {
        when(clientRepository.findAll()).thenReturn(List.of(oro, diamante));
        when(interactionRepository.findTopByClientIdOrderByContactDateDesc("c-oro"))
                .thenReturn(interaccion("c-oro", LocalDateTime.now().minusDays(30)));
        when(interactionRepository.findTopByClientIdOrderByContactDateDesc("c-diamante"))
                .thenReturn(interaccion("c-diamante", LocalDateTime.now().minusDays(3)));
        SegmentCriteria criterio = SegmentCriteria.builder().minDaysWithoutContact(15).build();

        assertThat(ids(criterio)).containsExactly("c-oro");
    }

    @Test
    @DisplayName("El estado de propuesta se compara normalizado: 'In negotiation' = In_negotiation")
    void estadoDePropuestaSeNormaliza() {
        when(proposalRepository.findByClientId("c-diamante"))
                .thenReturn(List.of(propuesta("c-diamante", Proposal.ProposalStatus.In_negotiation)));
        SegmentCriteria criterio = SegmentCriteria.builder()
                .proposalStatuses(List.of("In negotiation")).build();

        assertThat(ids(criterio)).containsExactly("c-diamante");
    }

    @Test
    @DisplayName("Los criterios se combinan con Y logico: plan Oro y sin proyectos")
    void criteriosSeCombinanConYLogico() {
        Client oroConProyecto = cliente("c-oro-2", Client.Plan.Oro);
        when(clientRepository.findAll()).thenReturn(List.of(oro, oroConProyecto, diamante));
        when(projectRepository.countByClientId("c-oro-2")).thenReturn(1L);
        SegmentCriteria criterio = SegmentCriteria.builder()
                .plans(List.of("Oro")).hasProjects(false).build();

        assertThat(ids(criterio)).containsExactly("c-oro");
    }

    @Test
    @DisplayName("segmentIds devuelve solo los identificadores del segmento")
    void segmentIdsDevuelveIdentificadores() {
        SegmentCriteria criterio = SegmentCriteria.builder().plans(List.of("Diamante")).build();

        assertThat(service.segmentIds(criterio)).containsExactly("c-diamante");
    }

    // ---------------------------------------------------------------- utilidades

    private List<String> ids(SegmentCriteria criterio) {
        return service.segment(criterio).stream().map(Client::getClientId).toList();
    }

    private static Client cliente(String id, Client.Plan plan) {
        return Client.builder().clientId(id).userId(id).plan(plan).build();
    }

    private static MarketingInteraction interaccion(String clientId, LocalDateTime fecha) {
        return MarketingInteraction.builder()
                .clientId(clientId)
                .campaignId(1)
                .contactDate(fecha)
                .interactionType(MarketingInteraction.InteractionType.message)
                .build();
    }

    private static Proposal propuesta(String clientId, Proposal.ProposalStatus status) {
        return Proposal.builder().clientId(clientId).status(status).build();
    }
}
