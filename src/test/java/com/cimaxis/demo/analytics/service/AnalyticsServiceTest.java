package com.cimaxis.demo.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cimaxis.demo.analytics.mapper.KpiSnapshotMapper;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.InventoryRepository;
import com.cimaxis.demo.analytics.repository.KpiSnapshotRepository;
import com.cimaxis.demo.analytics.repository.ProductRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.analytics.repository.UserRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;
import com.cimaxis.demo.analytics.dto.ClientPlanDistributionDto;

class AnalyticsServiceTest {

    @Test
    void incluirEstadosCanonicosDeColaboracionEnProyectosEnCurso() {
        ClientRepository clients = mock(ClientRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        KpiSnapshotRepository snapshots = mock(KpiSnapshotRepository.class);
        CampaignRepository campaigns = mock(CampaignRepository.class);
        MarketingInteractionRepository interactions = mock(MarketingInteractionRepository.class);

        when(inventory.findAll()).thenReturn(List.of());
        when(campaigns.countByStatus(Campaign.CampaignStatus.Active)).thenReturn(0L);
        when(projects.countByStatusIn(any())).thenReturn(12L);

        new AnalyticsService(clients, users, products, inventory, projects, snapshots,
                campaigns, interactions, mock(KpiSnapshotMapper.class),
                mock(MarketingPlanRepository.class)).getSummary();

        ArgumentCaptor<Set<String>> statuses = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(projects).countByStatusIn(statuses.capture());
        assertThat(statuses.getValue()).contains("in_progress", "in_review");
    }

    @Test
    void getClientActivitiesUsaConsultaAgregadaSinConsultasNMasUno() {
        ClientRepository clients = mock(ClientRepository.class);
        UserRepository users = mock(UserRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        KpiSnapshotRepository snapshots = mock(KpiSnapshotRepository.class);
        CampaignRepository campaigns = mock(CampaignRepository.class);
        MarketingInteractionRepository interactions = mock(MarketingInteractionRepository.class);

        com.cimaxis.demo.analytics.dto.ClientActivitySummaryProjection proj =
                new com.cimaxis.demo.analytics.dto.ClientActivitySummaryProjection() {
                    @Override public String getClientId() { return "client-1"; }
                    @Override public String getPlan() {
                        return "Oro";
                    }
                    @Override public long getCampaignCount() { return 5L; }
                    @Override public long getProjectCount() { return 2L; }
                };

        when(clients.findClientActivitiesSummary()).thenReturn(List.of(proj));

        AnalyticsService service = new AnalyticsService(clients, users, products, inventory,
                projects, snapshots, campaigns, interactions, mock(KpiSnapshotMapper.class),
                mock(MarketingPlanRepository.class));

        List<com.cimaxis.demo.analytics.dto.ClientActivityDto> result = service.getClientActivities();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo("client-1");
        assertThat(result.get(0).getPlan()).isEqualTo("Oro");
        assertThat(result.get(0).getCampaignCount()).isEqualTo(5L);
        assertThat(result.get(0).getProjectCount()).isEqualTo(2L);

        org.mockito.Mockito.verify(clients).findClientActivitiesSummary();
        org.mockito.Mockito.verifyNoInteractions(campaigns);
        org.mockito.Mockito.verifyNoInteractions(projects);
    }

    @Test
    void distribucionPorPlanSigueElCatalogoEIncluyePlanesFueraDelCatalogo() {
        ClientRepository clients = mock(ClientRepository.class);
        MarketingPlanRepository planes = mock(MarketingPlanRepository.class);
        when(planes.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(
                plan("Platinum", "#64748B", true),
                plan("Oro", "#D97706", true),
                plan("Bronce", "#A16207", true),
                plan("Esmeralda", "#059669", false),
                plan("Premium", "#7C3AED", false)));
        when(clients.countGroupedByPlan()).thenReturn(List.of(
                new Object[] {"Oro", 7L},
                new Object[] {"platinum", 6L},
                new Object[] {"Esmeralda", 2L},
                new Object[] {"Legado", 1L}));

        AnalyticsService service = new AnalyticsService(clients, mock(UserRepository.class),
                mock(ProductRepository.class), mock(InventoryRepository.class), mock(ProjectRepository.class),
                mock(KpiSnapshotRepository.class), mock(CampaignRepository.class),
                mock(MarketingInteractionRepository.class), mock(KpiSnapshotMapper.class), planes);

        List<ClientPlanDistributionDto> distribucion = service.getClientPlanDistribution();

        // Activos en el orden del catalogo (Bronce con 0), Esmeralda inactivo pero con clientes,
        // Premium inactivo sin clientes se omite y "Legado" (fuera del catalogo) va al final.
        assertThat(distribucion).extracting(ClientPlanDistributionDto::getPlan)
                .containsExactly("Platinum", "Oro", "Bronce", "Esmeralda", "Legado");
        assertThat(distribucion).extracting(ClientPlanDistributionDto::getClientCount)
                .containsExactly(6L, 7L, 0L, 2L, 1L);
        assertThat(distribucion.get(1).getColor()).isEqualTo("#D97706");
        assertThat(distribucion.get(4).getColor()).isNull();
    }

    private static MarketingPlan plan(String nombre, String color, boolean activo) {
        return MarketingPlan.builder().planId(UUID.randomUUID()).name(nombre).color(color)
                .active(activo).sortOrder(1).createdAt(LocalDateTime.now()).build();
    }
}
