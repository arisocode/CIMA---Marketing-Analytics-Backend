package com.cimaxis.demo.marketing.service.commercial;

import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.cliente;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.deTipo;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.dia;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.mensaje;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.propuesta;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.proyecto;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.respuesta;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction.InteractionType;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts.ClientFacts;

/** Hechos por cliente con las mismas definiciones de contacto y respuesta que los KPI. */
class CommercialFactsTest {

    @Test
    @DisplayName("Un correo SKIPPED_LIMIT no es contacto: no mueve la fecha del ultimo contacto")
    void skippedNoEsContacto() {
        CommercialFacts hechos = CommercialFacts.from(List.of(
                mensaje("c1", dia(10, 1), "QUEUED"),
                mensaje("c1", dia(10, 20), "SKIPPED_LIMIT"),
                mensaje("c2", dia(10, 5), "SKIPPED_LIMIT")), List.of(), List.of());

        assertThat(hechos.of("c1").lastContact()).isEqualTo(dia(10, 1));
        assertThat(hechos.of("c2")).isEqualTo(ClientFacts.EMPTY);
    }

    @Test
    @DisplayName("Respuesta = texto de respuesta o tipo click, inquiry, purchase o testimonial")
    void respuestas() {
        CommercialFacts hechos = CommercialFacts.from(List.of(
                respuesta("c1", dia(10, 3), "Me interesa"),
                deTipo("c2", dia(10, 4), InteractionType.purchase),
                deTipo("c3", dia(10, 5), InteractionType.no_response),
                mensaje("c4", dia(10, 6), "QUEUED")), List.of(), List.of());

        assertThat(hechos.of("c1").lastResponse()).isEqualTo(dia(10, 3));
        assertThat(hechos.of("c2").lastResponse()).isEqualTo(dia(10, 4));
        assertThat(hechos.of("c3").lastResponse()).isNull();
        assertThat(hechos.of("c4").lastResponse()).isNull();
    }

    @Test
    @DisplayName("Los conjuntos de contactados y respondieron coinciden con los del KPI")
    void mismosConjuntosQueKpi() {
        List<MarketingInteraction> interacciones = List.of(
                mensaje("c1", dia(10, 1), "QUEUED"),
                respuesta("c1", dia(10, 2), "Si"),
                mensaje("c2", dia(10, 3), "SKIPPED_LIMIT"),
                deTipo("c3", dia(10, 4), InteractionType.click));

        assertThat(KpiCalculationService.clientesContactados(interacciones)).containsExactly("c1", "c3");
        assertThat(KpiCalculationService.clientesQueRespondieron(interacciones)).containsExactly("c1", "c3");
    }

    @Test
    @DisplayName("Propuestas abiertas, aprobadas y proyecto (vinculado o en PROJECTS)")
    void propuestasYProyectos() {
        LocalDate oct = LocalDate.of(2026, 10, 1);
        CommercialFacts hechos = CommercialFacts.from(List.of(), List.of(
                propuesta(1, "c1", ProposalStatus.In_negotiation, oct, null, "100", null),
                propuesta(2, "c2", ProposalStatus.Approved, oct, oct, "200", "p-1"),
                propuesta(3, "c3", ProposalStatus.Approved, oct, oct, "300", "  "),
                propuesta(4, "c4", ProposalStatus.In_diagnosis, oct, null, "50", null)),
                List.of(proyecto("p-9", "c4", dia(9, 1))));

        assertThat(hechos.of("c1")).isEqualTo(new ClientFacts(null, null, true, false, false));
        assertThat(hechos.of("c2")).isEqualTo(new ClientFacts(null, null, false, true, true));
        assertThat(hechos.of("c3")).isEqualTo(new ClientFacts(null, null, false, true, false));
        assertThat(hechos.of("c4")).isEqualTo(new ClientFacts(null, null, false, false, true));
    }

    @Test
    @DisplayName("Nombre: additionalInfo, o contactInfo si no hay nombre")
    void nombre() {
        assertThat(CommercialFacts.nombre(cliente("c1", "Ana", "ana@cima.co", null, null))).isEqualTo("Ana");
        assertThat(CommercialFacts.nombre(cliente("c2", " ", "luis@cima.co", null, null))).isEqualTo("luis@cima.co");
        assertThat(CommercialFacts.nombre(null)).isNull();
    }

    @Test
    @DisplayName("Titulo de propuesta: primera linea de la descripcion o Propuesta #id")
    void tituloPropuesta() {
        Proposal conDescripcion = Proposal.builder().proposalId(3).description("Branding 2027\nDetalle largo").build();
        Proposal sinDescripcion = Proposal.builder().proposalId(4).build();
        Proposal larga = Proposal.builder().proposalId(5).description("x".repeat(100)).build();

        assertThat(CommercialFacts.tituloPropuesta(conDescripcion)).isEqualTo("Branding 2027");
        assertThat(CommercialFacts.tituloPropuesta(sinDescripcion)).isEqualTo("Propuesta #4");
        assertThat(CommercialFacts.tituloPropuesta(larga)).hasSize(80);
    }
}
