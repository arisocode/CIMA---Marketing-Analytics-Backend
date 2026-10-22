package com.cimaxis.demo.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Formatos en español de Colombia de los reportes descargables. */
class ReportFormatterTest {

    @Test
    @DisplayName("Conteos como enteros sin decimales y con punto de miles")
    void enteros() {
        assertThat(ReportFormatter.entero(2)).isEqualTo("2");
        assertThat(ReportFormatter.entero(new BigDecimal("2.0"))).isEqualTo("2");
        assertThat(ReportFormatter.entero(1250L)).isEqualTo("1.250");
        assertThat(ReportFormatter.entero(null)).isEqualTo("0");
    }

    @Test
    @DisplayName("Dinero en pesos colombianos: $ 4.500.000, sin decimales")
    void moneda() {
        assertThat(ReportFormatter.moneda(new BigDecimal("4500000"))).isEqualTo("$ 4.500.000");
        assertThat(ReportFormatter.moneda(new BigDecimal("4500000.60"))).isEqualTo("$ 4.500.001");
        assertThat(ReportFormatter.moneda(new BigDecimal("950"))).isEqualTo("$ 950");
        assertThat(ReportFormatter.moneda(new BigDecimal("-25000"))).isEqualTo("-$ 25.000");
        assertThat(ReportFormatter.moneda(null)).isEqualTo("$ 0");
    }

    @Test
    @DisplayName("Porcentajes con coma decimal y símbolo")
    void porcentajes() {
        assertThat(ReportFormatter.porcentaje(new BigDecimal("26.67"))).isEqualTo("26,67 %");
        assertThat(ReportFormatter.porcentaje(new BigDecimal("50"))).isEqualTo("50,00 %");
        assertThat(ReportFormatter.porcentaje(new BigDecimal("33.333"))).isEqualTo("33,33 %");
        assertThat(ReportFormatter.porcentaje(null)).isEqualTo("0,00 %");
    }

    @Test
    @DisplayName("Días con una cifra decimal y unidad")
    void dias() {
        assertThat(ReportFormatter.dias(new BigDecimal("19"))).isEqualTo("19,0 días");
        assertThat(ReportFormatter.dias(new BigDecimal("2.25"))).isEqualTo("2,3 días");
        assertThat(ReportFormatter.dias(new BigDecimal("1234.5"))).isEqualTo("1.234,5 días");
    }

    @Test
    @DisplayName("Variación frente al período anterior: ▲ verde, ▼ rojo, sin dato si el anterior es 0")
    void variacion() {
        assertThat(ReportFormatter.variacion(new BigDecimal("9"), new BigDecimal("8"))).isEqualTo("▲ +12,5 %");
        assertThat(ReportFormatter.variacion(new BigDecimal("6"), new BigDecimal("8"))).isEqualTo("▼ -25,0 %");
        assertThat(ReportFormatter.variacion(new BigDecimal("8"), new BigDecimal("8"))).isEqualTo("= 0,0 %");
        assertThat(ReportFormatter.variacion(new BigDecimal("5"), BigDecimal.ZERO)).isEqualTo("sin dato");
        assertThat(ReportFormatter.variacion(new BigDecimal("5"), null)).isEqualTo("sin dato");
    }

    @Test
    @DisplayName("Fechas en español con a. m. y p. m.")
    void fechas() {
        assertThat(ReportFormatter.fecha(LocalDate.of(2026, 10, 22))).isEqualTo("22 de octubre de 2026");
        assertThat(ReportFormatter.fechaHora(LocalDateTime.of(2026, 10, 22, 16, 36)))
                .isEqualTo("22 de octubre de 2026, 4:36 p. m.");
        assertThat(ReportFormatter.fechaHora(LocalDateTime.of(2026, 1, 5, 0, 5)))
                .isEqualTo("5 de enero de 2026, 12:05 a. m.");
        assertThat(ReportFormatter.fechaHora(LocalDateTime.of(2026, 3, 1, 12, 0)))
                .isEqualTo("1 de marzo de 2026, 12:00 p. m.");
        assertThat(ReportFormatter.fecha((LocalDate) null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Un instante se muestra en hora de Bogotá")
    void zonaBogota() {
        ZonedDateTime utc = ZonedDateTime.of(2026, 10, 22, 21, 36, 0, 0, ZoneOffset.UTC);
        assertThat(ReportFormatter.fechaHora(utc)).isEqualTo("22 de octubre de 2026, 4:36 p. m.");
    }

    @Test
    @DisplayName("Períodos como 'Octubre 2026' y rangos de períodos")
    void periodos() {
        assertThat(ReportFormatter.periodo("2026-10")).isEqualTo("Octubre 2026");
        assertThat(ReportFormatter.periodo("2026-01")).isEqualTo("Enero 2026");
        assertThat(ReportFormatter.periodo("otro")).isEqualTo("otro");
        assertThat(ReportFormatter.rangoPeriodos(List.of("2026-10", "2026-08", "2026-09")))
                .isEqualTo("Agosto 2026 a Octubre 2026");
        assertThat(ReportFormatter.rangoPeriodos(List.of("2026-10"))).isEqualTo("Octubre 2026");
        assertThat(ReportFormatter.rangoPeriodos(List.of())).isEqualTo("Sin períodos");
    }

    @Test
    @DisplayName("Tipos y estados de campaña traducidos igual que el frontend")
    void traducciones() {
        assertThat(ReportFormatter.tipoCampana("Positioning")).isEqualTo("Posicionamiento");
        assertThat(ReportFormatter.tipoCampana("Direct_sales")).isEqualTo("Venta directa");
        assertThat(ReportFormatter.tipoCampana("Value_content")).isEqualTo("Contenido de valor");
        assertThat(ReportFormatter.tipoCampana("Testimonial")).isEqualTo("Testimoniales");
        assertThat(ReportFormatter.tipoCampana("Reactivation")).isEqualTo("Reactivación");
        assertThat(ReportFormatter.estadoCampana("Draft")).isEqualTo("Borrador");
        assertThat(ReportFormatter.estadoCampana("Active")).isEqualTo("Activa");
        assertThat(ReportFormatter.estadoCampana("Paused")).isEqualTo("Pausada");
        assertThat(ReportFormatter.estadoCampana("Completed")).isEqualTo("Completada");
        assertThat(ReportFormatter.estadoCampana("Cancelled")).isEqualTo("Cancelada");
    }

    @Test
    @DisplayName("Estados de propuestas y proyectos; un valor desconocido se muestra legible")
    void estados() {
        assertThat(ReportFormatter.estado("Approved")).isEqualTo("Aprobada");
        assertThat(ReportFormatter.estado("In_negotiation")).isEqualTo("En negociación");
        assertThat(ReportFormatter.estado("in_progress")).isEqualTo("En curso");
        assertThat(ReportFormatter.estadoProyecto("completed")).isEqualTo("Completado");
        assertThat(ReportFormatter.estadoProyecto("Approved")).isEqualTo("Aprobada");
        assertThat(ReportFormatter.estado("valor_raro")).isEqualTo("Valor raro");
        assertThat(ReportFormatter.estado(null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Textos largos se recortan con puntos suspensivos")
    void recortar() {
        String largo = "x".repeat(100);
        assertThat(ReportFormatter.recortar(largo, 80)).hasSize(80);
        assertThat(ReportFormatter.recortar(largo, 80)).endsWith("…");
        assertThat(ReportFormatter.recortar("  corto  ", 80)).isEqualTo("corto");
    }
}
