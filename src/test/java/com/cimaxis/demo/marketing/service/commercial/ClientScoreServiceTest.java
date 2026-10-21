package com.cimaxis.demo.marketing.service.commercial;

import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.RELOJ;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.cliente;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.dia;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.mensaje;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.propuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.dto.commercial.MarketingClientResponse;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/** Puntaje en el listado de clientes de marketing. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientScoreServiceTest {

    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private ProjectRepository projectRepository;

    private ClientScoreService service;

    @BeforeEach
    void setUp() {
        when(interactionRepository.findAll()).thenReturn(List.of(mensaje("c1", dia(10, 18), "QUEUED")));
        when(proposalRepository.findAll()).thenReturn(List.of(
                propuesta(1, "c1", ProposalStatus.Sent, LocalDate.of(2026, 10, 18), null, "100", null)));
        when(projectRepository.findAll()).thenReturn(List.of());
        service = new ClientScoreService(interactionRepository, proposalRepository, projectRepository, RELOJ);
    }

    @Test
    @DisplayName("Conserva los campos del cliente y agrega score")
    void agregaScore() {
        Client ana = cliente("c1", "Ana", "ana@cima.co", "Oro", dia(9, 1));

        List<MarketingClientResponse> listado = service.conPuntaje(List.of(ana));

        MarketingClientResponse fila = listado.get(0);
        assertThat(fila.clientId()).isEqualTo("c1");
        assertThat(fila.userId()).isEqualTo("u-c1");
        assertThat(fila.contactInfo()).isEqualTo("ana@cima.co");
        assertThat(fila.additionalInfo()).isEqualTo("Ana");
        assertThat(fila.plan()).isEqualTo("Oro");
        assertThat(fila.score()).isEqualTo(20);
    }

    @Test
    @DisplayName("Hoy se calcula en America/Bogota")
    void hoyEnBogota() {
        assertThat(service.hoy()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("Listado vacio no consulta los repositorios")
    void listadoVacio() {
        assertThat(service.conPuntaje(List.of())).isEmpty();
        verify(interactionRepository, never()).findAll();
    }
}
