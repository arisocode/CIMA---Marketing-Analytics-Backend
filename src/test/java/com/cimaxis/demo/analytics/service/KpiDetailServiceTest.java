package com.cimaxis.demo.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.KpiSnapshot;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.dto.KpiComparisonDto;
import com.cimaxis.demo.analytics.dto.KpiDetailDto;
import com.cimaxis.demo.analytics.dto.KpiDetailItemDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.dto.ProjectAnalyticsDto;
import com.cimaxis.demo.analytics.mapper.KpiSnapshotMapper;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.KpiSnapshotRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiDetailServiceTest {

    private static final String OCTUBRE = "2026-10";
    private static final LocalDateTime CIERRE = LocalDateTime.of(2026, 10, 7, 0, 36);

    @Mock private ClientRepository clientRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private KpiSnapshotRepository kpiSnapshotRepository;

    private KpiCalculationService kpiCalculationService;
    private KpiDetailService service;

    private final Client ana = cliente("c-1", "Muebles Prim", Client.Plan.Oro, CIERRE.minusDays(19));
    private final Client luis = cliente("c-2", "Petal Restore", Client.Plan.Diamante, CIERRE.minusDays(40));
    private final Client sinNombre = cliente("c-3", null, null, null);

    @BeforeEach
    void setUp() {
        KpiSnapshotMapper mapper = new KpiSnapshotMapper();
        kpiCalculationService = new KpiCalculationService(clientRepository, projectRepository,
                proposalRepository, campaignRepository, interactionRepository, kpiSnapshotRepository, mapper);
        service = new KpiDetailService(kpiCalculationService, clientRepository, projectRepository,
                proposalRepository, kpiSnapshotRepository, mapper);

        when(clientRepository.findAllById(anyIterable())).thenReturn(List.of(ana, luis, sinNombre));
        when(clientRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(ana, sinNombre));
        when(projectRepository.findByStatusInAndUpdatedAtBetween(any(), any(), any())).thenReturn(List.of(
                proyecto("p-1", "Muebles Prim web", "c-1", "completed", CIERRE),
                proyecto("p-2", "Sin cliente", null, "completed", CIERRE)));
        when(projectRepository.findByStatusIn(any())).thenReturn(List.of(
                proyecto("p-3", "Lanzamiento", "c-2", "in_progress", CIERRE),
                proyecto("p-4", "Catalogo", "c-1", "in_review", CIERRE)));
        when(proposalRepository.findByStatusAndResponseDateBetween(any(), any(), any())).thenReturn(List.of(
                propuesta(1, "c-1", "4500000"), propuesta(2, "c-2", "1800000")));
        when(campaignRepository.findActiveInPeriod(any(), any(), any())).thenReturn(List.of(
                Campaign.builder().campaignId(9).campaignName("Navidad").clientId("c-1")
                        .campaignType(Campaign.CampaignType.Positioning)
                        .status(Campaign.CampaignStatus.Active)
                        .startDate(LocalDate.of(2026, 10, 1)).build()));
        when(interactionRepository.findByContactDateBetween(any(), any())).thenReturn(List.of(
                interaccion("c-1", MarketingInteraction.InteractionType.message, null, 3),
                interaccion("c-1", MarketingInteraction.InteractionType.inquiry, null, 9),
                interaccion("c-2", MarketingInteraction.InteractionType.message, null, 4),
                interaccion("c-3", MarketingInteraction.InteractionType.no_response, null, 5)));
    }

    @Test
    @DisplayName("El total de cada detalle coincide siempre con el numero del KPI")
    void detalleCoincideConCadaKpi() {
        KpiSnapshotDto kpis = kpiCalculationService.calculate(OCTUBRE);

        assertThat(detalle("new_clients").total()).isEqualTo(kpis.getNewClients());
        assertThat(detalle("closed_projects").total()).isEqualTo(kpis.getClosedProjects());
        assertThat(detalle("active_campaigns").total()).isEqualTo(kpis.getActiveCampaigns());
        assertThat(detalle("clients_contacted").total()).isEqualTo(kpis.getClientsContacted());
        assertThat(detalle("projects_in_progress").total()).isEqualTo(kpis.getProjectsInProgress());

        // Ingresos: la suma de los valores listados es el valor de la tarjeta.
        KpiDetailDto ingresos = detalle("revenue");
        assertThat(suma(ingresos.items())).isEqualByComparingTo(kpis.getEstimatedRevenue());
        assertThat(ingresos.kpiValue()).isEqualByComparingTo(kpis.getEstimatedRevenue());

        // Tasa: clientes que respondieron sobre clientes contactados.
        KpiDetailDto respondieron = detalle("response_rate");
        BigDecimal tasa = BigDecimal.valueOf(respondieron.total()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(kpis.getClientsContacted()), 2, RoundingMode.HALF_UP);
        assertThat(tasa).isEqualByComparingTo(kpis.getResponseRate());
        assertThat(respondieron.kpiValue()).isEqualByComparingTo(kpis.getResponseRate());

        // Dias de cierre: el promedio de los valores listados es el valor de la tarjeta.
        KpiDetailDto cierre = detalle("avg_close_days");
        BigDecimal promedio = suma(cierre.items())
                .divide(BigDecimal.valueOf(cierre.total()), 2, RoundingMode.HALF_UP);
        assertThat(promedio).isEqualByComparingTo(kpis.getAvgCloseDays());
    }

    @Test
    @DisplayName("Clientes contactados: uno por cliente, con su ultima interaccion del mes")
    void clientesContactadosUnoPorCliente() {
        KpiDetailDto contactados = detalle("clients_contacted");

        assertThat(contactados.items()).extracting(KpiDetailItemDto::clientId)
                .containsExactlyInAnyOrder("c-1", "c-2", "c-3");
        KpiDetailItemDto prim = contactados.items().stream()
                .filter(i -> "c-1".equals(i.clientId())).findFirst().orElseThrow();
        assertThat(prim.title()).isEqualTo("Muebles Prim");
        assertThat(prim.date()).isEqualTo(LocalDateTime.of(2026, 10, 9, 10, 0));
        assertThat(prim.value()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("Tasa de respuesta: lista solo los clientes que respondieron")
    void tasaDeRespuestaListaQuienesRespondieron() {
        assertThat(detalle("response_rate").items()).extracting(KpiDetailItemDto::clientId)
                .containsExactly("c-1");
    }

    @Test
    @DisplayName("Dias de cierre: cada proyecto trae sus dias y se omiten los que no tienen cliente")
    void diasDeCierrePorProyecto() {
        KpiDetailDto cierre = detalle("avg_close_days");

        assertThat(cierre.items()).hasSize(1);
        assertThat(cierre.items().get(0).title()).isEqualTo("Muebles Prim web");
        assertThat(cierre.items().get(0).value()).isEqualByComparingTo("19");
        assertThat(detalle("closed_projects").items()).extracting(KpiDetailItemDto::subtitle)
                .contains("Sin cliente");
    }

    @Test
    @DisplayName("Ingresos: cada propuesta trae su valor, cliente y fecha de respuesta")
    void ingresosConValorYCliente() {
        KpiDetailItemDto primera = detalle("revenue").items().get(0);

        assertThat(primera.value()).isEqualByComparingTo("4500000");
        assertThat(primera.clientName()).isEqualTo("Muebles Prim");
        assertThat(primera.date()).isEqualTo(LocalDate.of(2026, 10, 15).atStartOfDay());
        assertThat(primera.title()).isEqualTo("Plan 1");
    }

    @Test
    @DisplayName("Sin nombre sincronizado, el cliente se muestra por su identificador")
    void clienteSinNombreUsaIdentificador() {
        assertThat(detalle("new_clients").items()).extracting(KpiDetailItemDto::title)
                .containsExactlyInAnyOrder("Muebles Prim", "c-3");
    }

    @Test
    @DisplayName("Un KPI que no existe se rechaza indicando los valores permitidos")
    void kpiInvalidoSeRechaza() {
        assertThatThrownBy(() -> service.getDetail("ventas", OCTUBRE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KPI invalido")
                .hasMessageContaining("new_clients");
    }

    @Test
    @DisplayName("La clave del KPI no distingue mayusculas y el periodo por defecto es el mes actual")
    void claveSinMayusculasYPeriodoPorDefecto() {
        KpiDetailDto dto = service.getDetail("NEW_CLIENTS", null);

        assertThat(dto.kpi()).isEqualTo("new_clients");
        assertThat(dto.period()).isEqualTo(java.time.YearMonth.now().toString());
    }

    @Test
    @DisplayName("Comparacion: usa el snapshot guardado del mes anterior y calcula la variacion")
    void comparaContraSnapshotDelMesAnterior() {
        KpiSnapshot septiembre = KpiSnapshot.builder().period("2026-09")
                .newClients(1).closedProjects(0).estimatedRevenue(new BigDecimal("5000000"))
                .activeCampaigns(1).clientsContacted(4).responseRate(BigDecimal.ZERO)
                .avgCloseDays(BigDecimal.ZERO).projectsInProgress(2).build();
        when(kpiSnapshotRepository.findByPeriod("2026-09")).thenReturn(Optional.of(septiembre));

        KpiComparisonDto dto = service.compare(OCTUBRE);

        assertThat(dto.period()).isEqualTo("2026-10");
        assertThat(dto.previousPeriod()).isEqualTo("2026-09");
        assertThat(dto.kpis()).hasSize(8);
        assertThat(delta(dto, "new_clients").deltaPercent()).isEqualByComparingTo("100.00");
        assertThat(delta(dto, "revenue").deltaPercent()).isEqualByComparingTo("26.00");
        assertThat(delta(dto, "clients_contacted").deltaPercent()).isEqualByComparingTo("-25.00");
        assertThat(delta(dto, "projects_in_progress").deltaPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Comparacion: si el valor anterior es cero la variacion es null")
    void variacionNullSiAnteriorEsCero() {
        assertThat(KpiDetailService.deltaPercent(new BigDecimal("3"), BigDecimal.ZERO)).isNull();
        assertThat(KpiDetailService.deltaPercent(new BigDecimal("3"), null)).isNull();
        assertThat(KpiDetailService.deltaPercent(new BigDecimal("12"), new BigDecimal("9")))
                .isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("Proyectos: filtra por estado sin distinguir mayusculas y suma lo aprobado del cliente")
    void proyectosFiltradosConValorAprobado() {
        when(projectRepository.findAll()).thenReturn(List.of(
                proyecto("p-1", "Muebles Prim web", "c-1", "completed", CIERRE),
                proyecto("p-3", "Lanzamiento", "c-2", "in_progress", CIERRE.minusDays(2)),
                proyecto("p-5", "Interno", null, "in_progress", CIERRE.minusDays(1))));
        when(proposalRepository.findByStatus(Proposal.ProposalStatus.Approved)).thenReturn(List.of(
                propuesta(1, "c-2", "1800000"), propuesta(2, "c-2", "700000")));

        List<ProjectAnalyticsDto> enCurso = service.getProjects("IN_PROGRESS", null);

        assertThat(enCurso).extracting(ProjectAnalyticsDto::projectId).containsExactly("p-5", "p-3");
        ProjectAnalyticsDto lanzamiento = enCurso.get(1);
        assertThat(lanzamiento.clientName()).isEqualTo("Petal Restore");
        assertThat(lanzamiento.approvedProposalsValue()).isEqualByComparingTo("2500000");
        assertThat(enCurso.get(0).approvedProposalsValue()).isEqualByComparingTo("0");
        assertThat(service.getProjects(null, "c-1")).extracting(ProjectAnalyticsDto::projectId)
                .containsExactly("p-1");
    }

    private KpiDetailDto detalle(String kpi) {
        return service.getDetail(kpi, OCTUBRE);
    }

    private static BigDecimal suma(List<KpiDetailItemDto> items) {
        return items.stream().map(KpiDetailItemDto::value)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static KpiComparisonDto.KpiDelta delta(KpiComparisonDto dto, String kpi) {
        return dto.kpis().stream().filter(d -> d.kpi().equals(kpi)).findFirst().orElseThrow();
    }

    private static Client cliente(String id, String nombre, Client.Plan plan, LocalDateTime createdAt) {
        return Client.builder().clientId(id).userId(id).additionalInfo(nombre)
                .plan(plan).createdAt(createdAt).build();
    }

    private static Project proyecto(String id, String nombre, String clientId, String status,
                                    LocalDateTime updatedAt) {
        return Project.builder().projectId(id).projectName(nombre).clientId(clientId)
                .status(status).updatedAt(updatedAt).build();
    }

    private static Proposal propuesta(int id, String clientId, String valor) {
        return Proposal.builder().proposalId(id).clientId(clientId).description("Plan " + id)
                .status(Proposal.ProposalStatus.Approved).estimatedValue(new BigDecimal(valor))
                .responseDate(LocalDate.of(2026, 10, 15)).build();
    }

    private static MarketingInteraction interaccion(String clientId,
                                                    MarketingInteraction.InteractionType tipo,
                                                    String respuesta, int dia) {
        return MarketingInteraction.builder().clientId(clientId).campaignId(9)
                .interactionType(tipo).response(respuesta)
                .contactDate(LocalDateTime.of(2026, 10, dia, 10, 0)).build();
    }
}
