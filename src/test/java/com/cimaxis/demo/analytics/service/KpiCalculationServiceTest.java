package com.cimaxis.demo.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.KpiSnapshot;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.mapper.KpiSnapshotMapper;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.KpiSnapshotRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Calculo de los ocho KPIs de gestion (Tabla 7 del informe) y su consolidacion
 * mensual en KPI_SNAPSHOTS. Los repositorios se simulan: cada caso fija los
 * conteos de entrada y verifica el indicador resultante y el periodo consultado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiCalculationServiceTest {

    private static final String FEBRERO = "2026-02";

    @Mock private ClientRepository clientRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private KpiSnapshotRepository kpiSnapshotRepository;

    private KpiCalculationService service;

    @BeforeEach
    void setUp() {
        service = new KpiCalculationService(clientRepository, projectRepository, proposalRepository,
                campaignRepository, interactionRepository, kpiSnapshotRepository, new KpiSnapshotMapper());
        when(projectRepository.findByStatusInAndUpdatedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(proposalRepository.sumValueByStatusBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- periodo

    @Test
    @DisplayName("Un periodo con formato distinto a YYYY-MM se rechaza")
    void periodoInvalidoSeRechaza() {
        assertThatThrownBy(() -> service.calculate("02-2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato esperado YYYY-MM");
    }

    @Test
    @DisplayName("Sin periodo se calcula el mes en curso")
    void sinPeriodoUsaElMesActual() {
        String esperado = YearMonth.now().toString();

        assertThat(service.calculate(null).getPeriod()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("El periodo abarca del primer segundo al ultimo segundo del mes")
    void periodoCubreElMesCompleto() {
        service.calculate(FEBRERO);

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(clientRepository).countByCreatedAtBetween(desde.capture(), hasta.capture());
        assertThat(desde.getValue()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0, 0));
        assertThat(hasta.getValue()).isEqualTo(LocalDateTime.of(2026, 2, 28, 23, 59, 59));
    }

    // ---------------------------------------------------------------- KPIs comerciales

    @Test
    @DisplayName("KPI 1 - Clientes nuevos: cuenta los clientes creados en el mes")
    void clientesNuevos() {
        when(clientRepository.countByCreatedAtBetween(any(), any())).thenReturn(4L);

        assertThat(service.calculate(FEBRERO).getNewClients()).isEqualTo(4);
    }

    @Test
    @DisplayName("KPI 2 - Proyectos cerrados: cuenta los cerrados dentro del mes")
    void proyectosCerrados() {
        when(projectRepository.findByStatusInAndUpdatedAtBetween(any(), any(), any()))
                .thenReturn(List.of(proyecto("p-1", null, null), proyecto("p-2", null, null)));

        assertThat(service.calculate(FEBRERO).getClosedProjects()).isEqualTo(2);
    }

    @Test
    @DisplayName("KPI 3 - Ingresos: suma solo propuestas aprobadas con respuesta en el mes")
    void ingresosDePropuestasAprobadas() {
        when(proposalRepository.sumValueByStatusBetween(any(), any(), any()))
                .thenReturn(new BigDecimal("5400000"));

        KpiSnapshotDto kpis = service.calculate(FEBRERO);

        assertThat(kpis.getEstimatedRevenue()).isEqualByComparingTo("5400000");
        verify(proposalRepository).sumValueByStatusBetween(Proposal.ProposalStatus.Approved,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("KPI 3 - Ingresos: si no hay aprobadas el valor es cero, no nulo")
    void ingresosSinAprobadasEsCero() {
        when(proposalRepository.sumValueByStatusBetween(any(), any(), any())).thenReturn(null);

        assertThat(service.calculate(FEBRERO).getEstimatedRevenue()).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- KPIs de marketing

    @Test
    @DisplayName("KPI 4 - Campanas activas: cuenta las campanas en estado Active vigentes en el mes")
    void campanasActivas() {
        when(campaignRepository.countActiveInPeriod(eq(Campaign.CampaignStatus.Active), any(), any()))
                .thenReturn(3L);

        assertThat(service.calculate(FEBRERO).getActiveCampaigns()).isEqualTo(3);
    }

    @Test
    @DisplayName("KPI 5 - Clientes contactados: cuenta clientes distintos con interaccion en el mes")
    void clientesContactados() {
        when(interactionRepository.countDistinctClientsContactedBetween(any(), any())).thenReturn(8L);

        assertThat(service.calculate(FEBRERO).getClientsContacted()).isEqualTo(8);
    }

    @Test
    @DisplayName("KPI 6 - Tasa de respuesta: respuestas / contactados x 100 con dos decimales")
    void tasaDeRespuesta() {
        when(interactionRepository.countDistinctClientsContactedBetween(any(), any())).thenReturn(8L);
        when(interactionRepository.countResponsesBetween(any(), any(), anyList())).thenReturn(3L);

        assertThat(service.calculate(FEBRERO).getResponseRate()).isEqualByComparingTo("37.50");
    }

    @Test
    @DisplayName("KPI 6 - Tasa de respuesta: sin clientes contactados es cero (sin dividir por cero)")
    void tasaDeRespuestaSinContactadosEsCero() {
        when(interactionRepository.countResponsesBetween(any(), any(), anyList())).thenReturn(5L);

        assertThat(service.calculate(FEBRERO).getResponseRate()).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- KPIs operativos

    @Test
    @DisplayName("KPI 7 - Tiempo de cierre: promedia los dias entre alta del cliente y cierre")
    void tiempoPromedioDeCierre() {
        LocalDateTime cierre = LocalDateTime.of(2026, 2, 20, 10, 0);
        when(projectRepository.findByStatusInAndUpdatedAtBetween(any(), any(), any())).thenReturn(List.of(
                proyecto("p-1", "c-1", cierre),
                proyecto("p-2", "c-2", cierre),
                proyecto("p-3", null, cierre)));
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(clienteCreado("c-1", cierre.minusDays(10))));
        when(clientRepository.findById("c-2")).thenReturn(Optional.of(clienteCreado("c-2", cierre.minusDays(20))));

        assertThat(service.calculate(FEBRERO).getAvgCloseDays()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("KPI 8 - Proyectos en curso: incluye el estado canonico in_progress de colaboracion")
    @SuppressWarnings("unchecked")
    void proyectosEnCursoIncluyeEstadoCanonico() {
        when(projectRepository.countByStatusIn(any())).thenReturn(6L);

        KpiSnapshotDto kpis = service.calculate(FEBRERO);

        ArgumentCaptor<Collection<String>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(projectRepository).countByStatusIn(estados.capture());
        assertThat(estados.getValue()).contains("in_progress", "in_review");
        assertThat(kpis.getProjectsInProgress()).isEqualTo(6);
    }

    // ---------------------------------------------------------------- consolidacion

    @Test
    @DisplayName("Consolidar un mes ya existente actualiza el mismo snapshot (un solo registro por mes)")
    void consolidarActualizaElSnapshotDelMes() {
        KpiSnapshot existente = KpiSnapshot.builder().snapshotsId(11).period(FEBRERO).newClients(1).build();
        when(kpiSnapshotRepository.findByPeriod(FEBRERO)).thenReturn(Optional.of(existente));
        when(kpiSnapshotRepository.save(any(KpiSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.countByCreatedAtBetween(any(), any())).thenReturn(4L);

        KpiSnapshotDto guardado = service.calculateAndStore(FEBRERO, "gerente-sub");

        assertThat(guardado.getSnapshotsId()).isEqualTo(11);
        assertThat(guardado.getNewClients()).isEqualTo(4);
        assertThat(guardado.getCalculatedBy()).isEqualTo("gerente-sub");
        assertThat(guardado.getCalculatedAt()).isNotNull();
    }

    // ---------------------------------------------------------------- utilidades

    private static Project proyecto(String id, String clientId, LocalDateTime updatedAt) {
        return Project.builder().projectId(id).projectName(id).clientId(clientId)
                .status("completed").updatedAt(updatedAt).build();
    }

    private static Client clienteCreado(String id, LocalDateTime createdAt) {
        return Client.builder().clientId(id).userId(id).createdAt(createdAt).build();
    }
}
