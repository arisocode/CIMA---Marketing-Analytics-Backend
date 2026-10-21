package com.cimaxis.demo.marketing.service.campaigns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalRequest;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalResponse;
import com.cimaxis.demo.marketing.mapper.campaigns.ProposalMapper;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;

/**
 * Ciclo de vida de las propuestas comerciales (CU-08, RF-06 y RF-07).
 * El mapper es el real: se prueba la traduccion de estados junto con la regla.
 */
@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    private static final String CLIENT_ID = "client-1";

    @Mock private ProposalRepository proposalRepository;
    @Mock private ClientRepository clientRepository;

    private ProposalService service;

    @BeforeEach
    void setUp() {
        service = new ProposalService(proposalRepository, clientRepository, new ProposalMapper());
    }

    // ---------------------------------------------------------------- creacion

    @Test
    @DisplayName("No se crea una propuesta para un cliente que no existe en el CRM")
    void rechazaPropuestaDeClienteInexistente() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("el cliente no existe");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se crea una propuesta sin cliente asociado")
    void rechazaPropuestaSinCliente() {
        ProposalRequest sinCliente = ProposalRequest.builder().description("Branding").build();

        assertThatThrownBy(() -> service.create(sinCliente))
                .isInstanceOf(IllegalArgumentException.class);
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una propuesta nueva sin estado queda En diagnostico con fecha de hoy")
    void propuestaNuevaQuedaEnDiagnostico() {
        clienteExiste();
        guardarDevuelveLoMismo();

        ProposalResponse creada = service.create(request(null));

        assertThat(creada.getStatus()).isEqualTo("In_diagnosis");
        assertThat(creada.getCreatedDate()).isEqualTo(LocalDate.now());
        assertThat(creada.getEstimatedValue()).isEqualByComparingTo("1800000");
    }

    @Test
    @DisplayName("Si la propuesta llega con estado Enviada, se respeta ese estado")
    void propuestaNuevaRespetaEstadoInicial() {
        clienteExiste();
        guardarDevuelveLoMismo();

        assertThat(service.create(request("Sent")).getStatus()).isEqualTo("Sent");
    }

    // ---------------------------------------------------------------- cambios de estado

    @Test
    @DisplayName("Al aprobarse se registra la fecha de respuesta (alimenta el KPI de ingresos)")
    void aprobarRegistraFechaDeRespuesta() {
        when(proposalRepository.findById(7)).thenReturn(Optional.of(propuesta(Proposal.ProposalStatus.Sent)));
        guardarDevuelveLoMismo();

        ProposalResponse aprobada = service.changeStatus(7, "Approved");

        assertThat(aprobada.getStatus()).isEqualTo("Approved");
        assertThat(aprobada.getResponseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Pasar a En negociacion no cierra la propuesta ni fija fecha de respuesta")
    void negociacionNoFijaFechaDeRespuesta() {
        when(proposalRepository.findById(7)).thenReturn(Optional.of(propuesta(Proposal.ProposalStatus.Sent)));
        guardarDevuelveLoMismo();

        ProposalResponse enNegociacion = service.changeStatus(7, "In_negotiation");

        assertThat(enNegociacion.getStatus()).isEqualTo("In_negotiation");
        assertThat(enNegociacion.getResponseDate()).isNull();
    }

    @Test
    @DisplayName("Un estado que no existe se rechaza indicando los valores permitidos")
    void estadoInvalidoSeRechaza() {
        assertThatThrownBy(() -> service.changeStatus(7, "Ganada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valores permitidos");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cambiar el estado sin indicar uno nuevo se rechaza")
    void estadoVacioSeRechaza() {
        assertThatThrownBy(() -> service.changeStatus(7, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El estado es obligatorio");
    }

    @Test
    @DisplayName("Eliminar una propuesta inexistente responde recurso no encontrado")
    void eliminarInexistenteLanzaNoEncontrado() {
        when(proposalRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Propuesta no encontrado: 99");
        verify(proposalRepository, never()).deleteById(any());
    }

    // ---------------------------------------------------------------- proyecto vinculado

    @Test
    @DisplayName("Se vincula un proyecto a una propuesta aprobada y se expone en la respuesta")
    void vinculaProyectoAPropuestaAprobada() {
        when(proposalRepository.findById(7)).thenReturn(Optional.of(propuesta(Proposal.ProposalStatus.Approved)));
        guardarDevuelveLoMismo();

        ProposalResponse respuesta = service.linkProject(7, "  0b6f2c1e-1111-4a2b-9c3d-000000000001 ");

        assertThat(respuesta.getProjectId()).isEqualTo("0b6f2c1e-1111-4a2b-9c3d-000000000001");
        assertThat(respuesta.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("projectId null desvincula el proyecto")
    void desvinculaProyecto() {
        Proposal aprobada = propuesta(Proposal.ProposalStatus.Approved);
        aprobada.setProjectId("p-1");
        when(proposalRepository.findById(7)).thenReturn(Optional.of(aprobada));
        guardarDevuelveLoMismo();

        assertThat(service.linkProject(7, null).getProjectId()).isNull();
    }

    @Test
    @DisplayName("Solo las propuestas aprobadas aceptan proyecto")
    void rechazaProyectoEnPropuestaNoAprobada() {
        when(proposalRepository.findById(7)).thenReturn(Optional.of(propuesta(Proposal.ProposalStatus.Sent)));

        assertThatThrownBy(() -> service.linkProject(7, "p-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propuesta aprobada");
        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("El identificador del proyecto admite maximo 36 caracteres")
    void rechazaProjectIdLargo() {
        when(proposalRepository.findById(7)).thenReturn(Optional.of(propuesta(Proposal.ProposalStatus.Approved)));

        assertThatThrownBy(() -> service.linkProject(7, "x".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("36");
        verify(proposalRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- utilidades

    private void clienteExiste() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
    }

    private void guardarDevuelveLoMismo() {
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProposalRequest request(String status) {
        return ProposalRequest.builder()
                .clientId(CLIENT_ID)
                .description("Plan Oro - administracion de redes")
                .status(status)
                .estimatedValue(new BigDecimal("1800000"))
                .build();
    }

    private Proposal propuesta(Proposal.ProposalStatus status) {
        return Proposal.builder()
                .proposalId(7)
                .clientId(CLIENT_ID)
                .status(status)
                .createdDate(LocalDate.now().minusDays(10))
                .build();
    }
}
