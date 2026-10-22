package com.cimaxis.demo.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;

/** Datos del reporte de campañas: nombre del cliente y orden por estado y fecha de inicio. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignReportServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private ClientRepository clientRepository;

    private CampaignReportService service;

    @BeforeEach
    void setUp() {
        when(campaignRepository.findAll()).thenReturn(List.of(
                campana("Cierre de año", Campaign.CampaignStatus.Completed, LocalDate.of(2026, 1, 10), "c-1"),
                campana("Oro Q4", Campaign.CampaignStatus.Active, LocalDate.of(2026, 10, 1), "c-1"),
                campana("Marca", Campaign.CampaignStatus.Paused, LocalDate.of(2026, 8, 1), "c-2"),
                campana("Lanzamiento", Campaign.CampaignStatus.Active, LocalDate.of(2026, 9, 1), "c-x")));
        when(clientRepository.findAllById(any())).thenReturn(List.of(
                Client.builder().clientId("c-1").additionalInfo("Ana Pérez").build(),
                Client.builder().clientId("c-2").contactInfo("luis@cima.co").build()));
        service = new CampaignReportService(campaignRepository, clientRepository);
    }

    @Test
    @DisplayName("Ordena por estado (Activa, Pausada, Borrador, Completada, Cancelada) y luego por inicio")
    void orden() {
        assertThat(service.rows().stream().map(CampaignReportRow::name).toList())
                .containsExactly("Lanzamiento", "Oro Q4", "Marca", "Cierre de año");
    }

    @Test
    @DisplayName("Usa el nombre del cliente, no su id")
    void nombreDelCliente() {
        List<CampaignReportRow> filas = service.rows();

        assertThat(filas.get(1).clientName()).isEqualTo("Ana Pérez");
        assertThat(filas.get(2).clientName()).isEqualTo("luis@cima.co");
        assertThat(filas.get(0).clientName()).isEqualTo("Cliente no sincronizado");
        assertThat(filas.get(1).type()).isEqualTo("Direct_sales");
    }

    private static Campaign campana(String nombre, Campaign.CampaignStatus estado, LocalDate inicio, String clientId) {
        return Campaign.builder().campaignName(nombre).status(estado).startDate(inicio).clientId(clientId)
                .campaignType(Campaign.CampaignType.Direct_sales).build();
    }
}
