package com.cimaxis.demo.analytics.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cimaxis.demo.config.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Inventory;
import com.cimaxis.demo.analytics.dto.AnalyticsSummaryDto;
import com.cimaxis.demo.analytics.dto.CampaignStatusReportDto;
import com.cimaxis.demo.analytics.dto.ClientActivityDto;
import com.cimaxis.demo.analytics.dto.ClientPlanDistributionDto;
import com.cimaxis.demo.analytics.dto.InventoryAlertDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
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
import com.cimaxis.demo.marketing.service.plans.MarketingPlanService;

/**
 * Servicio que agrupa consultas y logica de negocio para generar
 * reportes y metricas a partir de las tablas del sistema.
 */
@Service
public class AnalyticsService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProjectRepository projectRepository;
    private final KpiSnapshotRepository kpiSnapshotRepository;
    private final CampaignRepository campaignRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final KpiSnapshotMapper kpiSnapshotMapper;
    private final MarketingPlanRepository planRepository;

    private static final Set<String> ESTADOS_EN_CURSO =
            Set.of(
                    "in_progress",
                    "in_review",
                    "in progress",
                    "active",
                    "en progreso",
                    "activo",
                    "en curso"
            );

    public AnalyticsService(
            ClientRepository clientRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            ProjectRepository projectRepository,
            KpiSnapshotRepository kpiSnapshotRepository,
            CampaignRepository campaignRepository,
            MarketingInteractionRepository interactionRepository,
            KpiSnapshotMapper kpiSnapshotMapper,
            MarketingPlanRepository planRepository) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.projectRepository = projectRepository;
        this.kpiSnapshotRepository = kpiSnapshotRepository;
        this.campaignRepository = campaignRepository;
        this.interactionRepository = interactionRepository;
        this.kpiSnapshotMapper = kpiSnapshotMapper;
        this.planRepository = planRepository;
    }

    public AnalyticsSummaryDto getSummary() {
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
                .activeCampaigns(campaignRepository.countByStatus(Campaign.CampaignStatus.Active))
                .totalProjects(projectRepository.count())
                .projectsInProgress(projectRepository.countByStatusIn(ESTADOS_EN_CURSO))
                .totalProducts(productRepository.count())
                .totalInventoryItems(inventoryRepository.count())
                .totalStock(totalStock)
                .lowStockAlerts(lowStockAlerts)
                .totalKpiSnapshots(kpiSnapshotRepository.count())
                .totalMarketingInteractions(interactionRepository.count())
                .build();
    }

    /**
     * Clientes por plan, en el orden del catalogo marketing_plans. Incluye los
     * planes activos (aunque tengan 0 clientes) y los inactivos que aun tienen
     * clientes. Si algun cliente tiene un plan que no esta en el catalogo, se
     * agrega al final para que el total cuadre.
     */
    public List<ClientPlanDistributionDto> getClientPlanDistribution() {
        Map<String, Long> conteo = new LinkedHashMap<>();
        Map<String, String> nombreOriginal = new LinkedHashMap<>();
        List<Object[]> filas = clientRepository.countGroupedByPlan();
        if (filas != null) {
            for (Object[] fila : filas) {
                if (fila == null || fila.length < 2 || fila[0] == null) continue;
                String clave = MarketingPlanService.clave(fila[0].toString());
                long total = fila[1] instanceof Number numero ? numero.longValue() : 0L;
                conteo.merge(clave, total, Long::sum);
                nombreOriginal.putIfAbsent(clave, fila[0].toString().trim());
            }
        }

        List<ClientPlanDistributionDto> distribution = new ArrayList<>();
        for (MarketingPlan plan : planRepository.findAllByOrderBySortOrderAscNameAsc()) {
            Long clientes = conteo.remove(MarketingPlanService.clave(plan.getName()));
            long total = clientes != null ? clientes : 0L;
            if (Boolean.TRUE.equals(plan.getActive()) || total > 0) {
                distribution.add(ClientPlanDistributionDto.builder()
                        .plan(plan.getName())
                        .clientCount(total)
                        .color(plan.getColor())
                        .build());
            }
        }
        conteo.forEach((clave, total) -> distribution.add(ClientPlanDistributionDto.builder()
                .plan(nombreOriginal.get(clave))
                .clientCount(total)
                .build()));
        return distribution;
    }

    public List<ClientActivityDto> getClientActivities() {
        return clientRepository.findClientActivitiesSummary().stream()
                .map(summary -> ClientActivityDto.builder()
                        .clientId(summary.getClientId())
                        .plan(summary.getPlan() != null ? summary.getPlan() : "Unknown")
                        .campaignCount(summary.getCampaignCount())
                        .projectCount(summary.getProjectCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Agrupa las campanas por estado.
     */
    public List<CampaignStatusReportDto> getCampaignStatusReport(LocalDate from,
                                                                 LocalDate to,
                                                                 String clientId) {
        List<Campaign> campaigns = campaignRepository.findAll().stream()
                .filter(c -> clientId == null || clientId.equals(c.getClientId()))
                .filter(c -> from == null || (c.getStartDate() != null && !c.getStartDate().isBefore(from)))
                .filter(c -> to == null || (c.getStartDate() != null && !c.getStartDate().isAfter(to)))
                .toList();

        Map<String, Long> statusCounts = campaigns.stream()
                .collect(Collectors.groupingBy(
                        campaign -> campaign.getStatus() != null ? campaign.getStatus().name() : "Unknown",
                        Collectors.counting()));

        return statusCounts.entrySet().stream()
                .map(entry -> CampaignStatusReportDto.builder()
                        .status(entry.getKey())
                        .campaignCount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /** Se conserva la firma sin filtros por compatibilidad. */
    public List<CampaignStatusReportDto> getCampaignStatusReport() {
        return getCampaignStatusReport(null, null, null);
    }

    public List<InventoryAlertDto> getLowStockAlerts() {
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

    /**
     * Historial completo de snapshots, del mas reciente al mas antiguo.
     */
    public List<KpiSnapshotDto> getKpiSnapshots() {
        return kpiSnapshotMapper.toDtoList(
                kpiSnapshotRepository.findAllByOrderByCalculatedAtDesc());
    }

    public KpiSnapshotDto getKpiSnapshotByPeriod(String period) {
        return kpiSnapshotRepository.findByPeriod(period)
                .map(kpiSnapshotMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay indicadores almacenados para el periodo " + period));
    }

    public List<KpiSnapshotDto> getKpiHistory(String from, String to) {
        return kpiSnapshotMapper.toDtoList(
                kpiSnapshotRepository.findByPeriodBetweenOrderByPeriodAsc(from, to));
    }
}
