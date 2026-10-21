package com.cimaxis.demo.marketing.service.commercial;

import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.HOY;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.cliente;
import static com.cimaxis.demo.marketing.service.commercial.CommercialTestData.dia;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts.ClientFacts;

/** Formula del puntaje de interes (0-100). Hoy = 2026-10-21. */
class InterestScoreTest {

    private final Client alta = cliente("c1", "Ana", "ana@cima.co", "Oro", dia(10, 20));

    @Test
    @DisplayName("Cliente con todo a favor y contactado ayer suma 100")
    void puntajeMaximo() {
        ClientFacts hechos = new ClientFacts(dia(10, 20), dia(10, 20), true, true, true);
        assertThat(InterestScore.calcular(alta, hechos, HOY)).isEqualTo(100);
    }

    @Test
    @DisplayName("Respuesta de hace exactamente 30 dias suma 30; de hace 31 no")
    void respuestaReciente() {
        ClientFacts hace30 = new ClientFacts(dia(10, 20), dia(9, 21), false, false, false);
        ClientFacts hace31 = new ClientFacts(dia(10, 20), dia(9, 20), false, false, false);
        assertThat(InterestScore.calcular(alta, hace30, HOY)).isEqualTo(30);
        assertThat(InterestScore.calcular(alta, hace31, HOY)).isEqualTo(0);
    }

    @Test
    @DisplayName("Propuesta abierta +20, aprobada +30, proyecto +20")
    void puntosPorPropuestasYProyecto() {
        assertThat(InterestScore.calcular(alta, new ClientFacts(dia(10, 20), null, true, false, false), HOY)).isEqualTo(20);
        assertThat(InterestScore.calcular(alta, new ClientFacts(dia(10, 20), null, false, true, false), HOY)).isEqualTo(30);
        assertThat(InterestScore.calcular(alta, new ClientFacts(dia(10, 20), null, false, true, true), HOY)).isEqualTo(50);
    }

    @Test
    @DisplayName("Resta 1 punto por cada dia sin contacto por encima de 15")
    void penalizacionPorDiasSinContacto() {
        // 20 dias sin contacto: 50 - 5 = 45
        ClientFacts hace20 = new ClientFacts(dia(10, 1), null, true, true, false);
        assertThat(InterestScore.calcular(alta, hace20, HOY)).isEqualTo(45);
        // 15 dias exactos: sin descuento
        ClientFacts hace15 = new ClientFacts(dia(10, 6), null, true, true, false);
        assertThat(InterestScore.calcular(alta, hace15, HOY)).isEqualTo(50);
    }

    @Test
    @DisplayName("Nunca contactado: los dias se cuentan desde el alta; el minimo es 0")
    void nuncaContactado() {
        Client antiguo = cliente("c2", "Luis", null, null, dia(1, 1));
        assertThat(InterestScore.calcular(antiguo, ClientFacts.EMPTY, HOY)).isEqualTo(0);

        Client reciente = cliente("c3", "Eva", null, null, dia(10, 1));
        // 20 dias desde el alta con propuesta abierta: 20 - 5 = 15
        assertThat(InterestScore.calcular(reciente, new ClientFacts(null, null, true, false, false), HOY))
                .isEqualTo(15);
    }

    @Test
    @DisplayName("Sin contacto ni fecha de alta no se descuenta")
    void sinFechas() {
        Client sinAlta = cliente("c4", "Sol", null, null, null);
        assertThat(InterestScore.calcular(sinAlta, new ClientFacts(null, null, true, false, false), HOY))
                .isEqualTo(20);
        assertThat(InterestScore.calcular(null, null, HOY)).isEqualTo(0);
    }

    @Test
    @DisplayName("Dias sin contacto: null si nunca fue contactado")
    void diasSinContacto() {
        assertThat(InterestScore.diasSinContacto(null, HOY)).isNull();
        assertThat(InterestScore.diasSinContacto(dia(10, 1), HOY)).isEqualTo(20L);
    }
}
