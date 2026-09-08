package com.cimaxis.demo.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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
        when(campaigns.findByStatus(Campaign.CampaignStatus.Active)).thenReturn(List.of());
        when(projects.countByStatusIn(any())).thenReturn(12L);

        new AnalyticsService(clients, users, products, inventory, projects, snapshots,
                campaigns, interactions, mock(KpiSnapshotMapper.class)).getSummary();

        ArgumentCaptor<Set<String>> statuses = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(projects).countByStatusIn(statuses.capture());
        assertThat(statuses.getValue()).contains("in_progress", "in_review");
    }
}
