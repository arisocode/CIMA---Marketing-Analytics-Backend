package com.cimaxis.demo.analytics.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Inventory;
import com.cimaxis.demo.analytics.domain.KpiSnapshot;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.dto.AnalyticsSummaryDto;
import com.cimaxis.demo.analytics.dto.CampaignStatusReportDto;
import com.cimaxis.demo.analytics.dto.ClientActivityDto;
import com.cimaxis.demo.analytics.dto.ClientPlanDistributionDto;
import com.cimaxis.demo.analytics.dto.InventoryAlertDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.InventoryRepository;
import com.cimaxis.demo.analytics.repository.KpiSnapshotRepository;
import com.cimaxis.demo.analytics.repository.ProductRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.analytics.repository.UserRepository;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;

@Service
public class AnalyticsService {

        /**
         * Servicio que agrupa consultas y lógica de negocio para generar
         * reportes y métricas a partir de las tablas del sistema.
         *
         * Este servicio debe permanecer libre de lógica HTTP y devolver DTOs
         * que luego son expuestos por el `AnalyticsController`.
         */

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProjectRepository projectRepository;
    private final KpiSnapshotRepository kpiSnapshotRepository;
    private final CampaignRepository campaignRepository;
    private final MarketingInteractionRepository interactionRepository;

    public AnalyticsService(
            ClientRepository clientRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            ProjectRepository projectRepository,
            KpiSnapshotRepository kpiSnapshotRepository,
            CampaignRepository campaignRepository,
            MarketingInteractionRepository interactionRepository) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.projectRepository = projectRepository;
        this.kpiSnapshotRepository = kpiSnapshotRepository;
        this.campaignRepository = campaignRepository;
        this.interactionRepository = interactionRepository;
    }

    public AnalyticsSummaryDto getSummary() {
                /**
                 * Construye un `AnalyticsSummaryDto` con métricas agregadas.
                 * - suma total del inventario
                 * - conteos de entidades principales
                 */
        List<Inventory> allInventory = inventoryRepository.findAll();
        long totalStock = allInventory.stream()
                .mapToLong(inv -> inv.getTotalStock() != null ? inv.getTotalStock() : 0)
                .sum();
        long lowStockAlerts = allInventory.stream()
                .filter(inv -> inv.getTotalStock() != null && inv.getLowStockAlert() != null)
                .filter(inv -> inv.getTotalStock() <= inv.getLowStockAlert())
                .count();

        return AnalyticsSummaryDto.builder()
                .totalClients(clientRepository.count())
                .totalUsers(userRepository.count())
                .totalCampaigns(campaignRepository.count())
                .activeCampaigns(campaignRepository.findByStatus(Campaign.CampaignStatus.Active).size())
                .totalProjects(projectRepository.count())
                .projectsInProgress(projectRepository.countByStatus("In Progress"))
                .totalProducts(productRepository.count())
                .totalInventoryItems(inventoryRepository.count())
                .totalStock(totalStock)
                .lowStockAlerts(lowStockAlerts)
                .totalKpiSnapshots(kpiSnapshotRepository.count())
                .totalMarketingInteractions(interactionRepository.count())
                .build();
    }

    public List<ClientPlanDistributionDto> getClientPlanDistribution() {
                /**
                 * Devuelve conteos de clientes por cada plan disponible.
                 */
        List<ClientPlanDistributionDto> distribution = new ArrayList<>();
        for (Client.Plan plan : Client.Plan.values()) {
            distribution.add(ClientPlanDistributionDto.builder()
                    .plan(plan.name())
                    .clientCount(clientRepository.countByPlan(plan))
                    .build());
        }
        return distribution;
    }

    public List<ClientActivityDto> getClientActivities() {
                /**
                 * Construye una lista con la actividad por cliente.
                 * Para cada cliente devuelve el número de campañas y proyectos.
                 */
        return clientRepository.findAll().stream()
                .map(client -> {
                    long campaignCount = campaignRepository.findByClientId(client.getClientId()).size();
                    long projectCount = projectRepository.countByClientId(client.getClientId());
                    return ClientActivityDto.builder()
                            .clientId(client.getClientId())
                            .plan(client.getPlan() != null ? client.getPlan().name() : "Unknown")
                            .campaignCount(campaignCount)
                            .projectCount(projectCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<CampaignStatusReportDto> getCampaignStatusReport() {
                /**
                 * Agrupa las campañas por estado y devuelve un DTO con los conteos.
                 */
        Map<String, Long> statusCounts = campaignRepository.findAll().stream()
                .collect(Collectors.groupingBy(campaign -> campaign.getStatus() != null ? campaign.getStatus().name() : "Unknown",
                        Collectors.counting()));

        return statusCounts.entrySet().stream()
                .map(entry -> CampaignStatusReportDto.builder()
                        .status(entry.getKey())
                        .campaignCount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    public List<InventoryAlertDto> getLowStockAlerts() {
                /**
                 * Busca en `INVENTORY` los items con `total_stock <= low_stock_alert`
                 * y devuelve un DTO con información adicional del producto.
                 */
        List<Inventory> allInventory = inventoryRepository.findAll();
        List<Integer> productIds = allInventory.stream()
                .filter(inv -> inv.getProductId() != null)
                .map(Inventory::getProductId)
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, String> namesByProduct = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(
                        product -> product.getProductId(),
                        product -> product.getProductName()));

        return allInventory.stream()
                .filter(inv -> inv.getTotalStock() != null && inv.getLowStockAlert() != null)
                .filter(inv -> inv.getTotalStock() <= inv.getLowStockAlert())
                .map(inv -> InventoryAlertDto.builder()
                        .inventoryId(inv.getInventoryId())
                        .productId(inv.getProductId())
                        .productName(namesByProduct.getOrDefault(inv.getProductId(), "Unknown product"))
                        .totalStock(inv.getTotalStock())
                        .pointOfSaleStock(inv.getPointOfSaleStock())
                        .lowStockAlert(inv.getLowStockAlert())
                        .inventoryType(inv.getInventoryType())
                        .build())
                .collect(Collectors.toList());
    }

    public List<KpiSnapshotDto> getKpiSnapshots() {
        /**
         * Devuelve los snapshots de KPI ordenados por fecha de cálculo.
         */
        return kpiSnapshotRepository.findAllByOrderByCalculatedAtDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private KpiSnapshotDto toDto(KpiSnapshot snapshot) {
        /**
         * Convierte la entidad `KpiSnapshot` a su DTO equivalente.
         */
        return KpiSnapshotDto.builder()
                .snapshotsId(snapshot.getSnapshotsId())
                .period(snapshot.getPeriod())
                .calculatedAt(snapshot.getCalculatedAt())
                .newClients(snapshot.getNewClients())
                .closedProjects(snapshot.getClosedProjects())
                .estimatedRevenue(snapshot.getEstimatedRevenue())
                .activeCampaigns(snapshot.getActiveCampaigns())
                .clientsContacted(snapshot.getClientsContacted())
                .responseRate(snapshot.getResponseRate())
                .avgCloseDays(snapshot.getAvgCloseDays())
                .projectsInProgress(snapshot.getProjectsInProgress())
                .calculatedBy(snapshot.getCalculatedBy())
                .build();
    }
}
