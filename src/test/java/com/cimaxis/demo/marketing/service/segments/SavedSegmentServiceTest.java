package com.cimaxis.demo.marketing.service.segments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentRequest;
import com.cimaxis.demo.marketing.dto.segments.SavedSegmentResponse;
import com.cimaxis.demo.marketing.dto.segments.SegmentClientResponse;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.segments.MarketingSegmentRepository;
import com.cimaxis.demo.marketing.service.commercial.ClientScoreService;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;
import com.cimaxis.demo.marketing.service.segmentation.ClientSegmentationService;
import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/** Segmentos guardados: CRUD, conteo de clientes y regla de desactivación. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavedSegmentServiceTest {

    @Mock private MarketingSegmentRepository segmentRepository;
    @Mock private ClientSegmentationService segmentationService;
    @Mock private CampaignRepository campaignRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ClientScoreService scoreService;

    private SavedSegmentService service;
    private MarketingSegment oro;

    @BeforeEach
    void setUp() {
        oro = MarketingSegment.builder().segmentId(UUID.randomUUID()).name("Oro sin contacto")
                .criteria("{\"plans\":[\"Oro\"],\"minDaysWithoutContact\":15}").active(true)
                .createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
        when(segmentRepository.findById(oro.getSegmentId())).thenReturn(Optional.of(oro));
        when(segmentRepository.findAllByOrderByNameAsc()).thenReturn(List.of(oro));
        when(segmentRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(segmentRepository.save(any(MarketingSegment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(segmentationService.segmentIds(any(SegmentCriteria.class))).thenReturn(List.of("c1", "c2"));
        when(scoreService.hechos()).thenReturn(CommercialFacts.from(List.of(), List.of(), List.of()));
        when(scoreService.hoy()).thenReturn(LocalDate.of(2026, 10, 21));
        service = new SavedSegmentService(segmentRepository, segmentationService, campaignRepository,
                clientRepository, scoreService);
    }

    @Test
    @DisplayName("Listado con el criterio como objeto y la cantidad actual de clientes")
    void listado() {
        SavedSegmentResponse r = service.list().get(0);

        assertThat(r.name()).isEqualTo("Oro sin contacto");
        assertThat(r.criteria().getPlans()).containsExactly("Oro");
        assertThat(r.criteria().getMinDaysWithoutContact()).isEqualTo(15);
        assertThat(r.clientCount()).isEqualTo(2L);
        assertThat(r.active()).isTrue();
    }

    @Test
    @DisplayName("Crear guarda el criterio en JSON y valida los planes con la segmentación")
    void crear() {
        SegmentCriteria criterio = SegmentCriteria.builder().plans(List.of("Diamante")).hasProjects(true).build();

        SavedSegmentResponse r = service.create(new SavedSegmentRequest(" Diamante con proyecto ", "VIP", criterio));

        assertThat(r.name()).isEqualTo("Diamante con proyecto");
        assertThat(r.description()).isEqualTo("VIP");
        assertThat(r.criteria().getHasProjects()).isTrue();
        verify(segmentationService, org.mockito.Mockito.times(2)).segmentIds(any(SegmentCriteria.class));
    }

    @Test
    @DisplayName("Nombre obligatorio y único; un plan inexistente responde 400")
    void validaciones() {
        assertThatThrownBy(() -> service.create(new SavedSegmentRequest(" ", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");

        when(segmentRepository.findByNameIgnoreCase("oro sin contacto")).thenReturn(Optional.of(oro));
        assertThatThrownBy(() -> service.create(new SavedSegmentRequest("oro sin contacto", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ya existe un segmento");

        when(segmentationService.segmentIds(any(SegmentCriteria.class)))
                .thenThrow(new IllegalArgumentException("Plan inválido: Bronce"));
        assertThatThrownBy(() -> service.create(new SavedSegmentRequest("Bronce", null,
                SegmentCriteria.builder().plans(List.of("Bronce")).build())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Bronce");
        verify(segmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Editar conserva el mismo nombre sin chocar consigo mismo")
    void editar() {
        when(segmentRepository.findByNameIgnoreCase("Oro sin contacto")).thenReturn(Optional.of(oro));

        SavedSegmentResponse r = service.update(oro.getSegmentId(),
                new SavedSegmentRequest("Oro sin contacto", "Nueva descripción", new SegmentCriteria()));

        assertThat(r.description()).isEqualTo("Nueva descripción");
        assertThat(oro.getCriteria()).isEqualTo("{}");
    }

    @Test
    @DisplayName("No se puede desactivar un segmento que usa una campaña activa (409)")
    void desactivarEnUso() {
        when(campaignRepository.findBySegmentIdAndStatus(oro.getSegmentId(), Campaign.CampaignStatus.Active))
                .thenReturn(List.of(Campaign.builder().campaignName("Reactivación Q4").build()));

        assertThatThrownBy(() -> service.setActive(oro.getSegmentId(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reactivación Q4");
        assertThat(oro.getActive()).isTrue();
    }

    @Test
    @DisplayName("Sin campañas activas se desactiva; requireActive lo rechaza después")
    void desactivar() {
        when(campaignRepository.findBySegmentIdAndStatus(any(), any())).thenReturn(List.of());

        assertThat(service.setActive(oro.getSegmentId(), false).active()).isFalse();
        assertThatThrownBy(() -> service.requireActive(oro.getSegmentId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inactivo");
    }

    @Test
    @DisplayName("Clientes del segmento con nombre, plan, último contacto y puntaje")
    void clientes() {
        when(clientRepository.findAllById(any())).thenReturn(List.of(
                Client.builder().clientId("c1").additionalInfo("Ana").plan("Oro").createdAt(LocalDateTime.of(2026, 10, 15, 9, 0)).build(),
                Client.builder().clientId("c2").contactInfo("luis@cima.co").plan("Oro").build()));

        List<SegmentClientResponse> clientes = service.clients(oro.getSegmentId());

        assertThat(clientes).hasSize(2);
        assertThat(clientes.stream().map(SegmentClientResponse::name).toList()).contains("Ana", "luis@cima.co");
        assertThat(clientes.get(0).plan()).isEqualTo("Oro");
    }

    @Test
    @DisplayName("Segmento inexistente: 404")
    void inexistente() {
        assertThatThrownBy(() -> service.clients(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }
}
