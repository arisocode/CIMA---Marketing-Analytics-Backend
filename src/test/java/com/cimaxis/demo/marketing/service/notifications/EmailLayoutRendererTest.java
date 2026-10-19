package com.cimaxis.demo.marketing.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailLayoutRendererTest {

    @Test
    void htmlUsaLaMarcaYElNombreDeLaEmpresaCuandoNoHayLogo() {
        String html = new EmailLayoutRenderer("", "CIMA", "", "", "").html("Hola", "Texto");

        assertThat(html).startsWith("<!DOCTYPE html>")
                .contains("lang=\"es\"")
                .contains("background:" + EmailLayoutRenderer.ROJO_CIMA)
                .contains(">CIMA</span>")
                .doesNotContain("<img");
    }

    @Test
    void htmlMuestraElLogoSoloConUnaUrlHttp() {
        String conLogo = new EmailLayoutRenderer("https://cdn.cima.test/logo.png", "CIMA", "", "", "")
                .html("Hola", "Texto");
        String sinUrlValida = new EmailLayoutRenderer("javascript:alert(1)", "CIMA", "", "", "")
                .html("Hola", "Texto");

        assertThat(conLogo).contains("<img src=\"https://cdn.cima.test/logo.png\"").contains("alt=\"CIMA\"");
        assertThat(sinUrlValida).doesNotContain("<img").doesNotContain("javascript:");
    }

    @Test
    void htmlEscapaElCuerpoYConvierteParrafosYSaltosDeLinea() {
        String html = new EmailLayoutRenderer("", "CIMA", "", "", "")
                .html("Asunto", "Hola Ana:\n\nPrimera linea\nsegunda <script>x</script>");

        assertThat(html).contains("<p style=\"margin:0 0 16px;\">Hola Ana:</p>")
                .contains("Primera linea<br>segunda &lt;script&gt;x&lt;/script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void pieIncluyeLosDatosDeContactoConfigurados() {
        EmailLayoutRenderer layout = new EmailLayoutRenderer("", "CIMA Agencia", "hola@cima.test",
                "+57 300 000 0000", "cima.test");

        assertThat(layout.html("A", "B")).contains("Correo: hola@cima.test")
                .contains("Teléfono: +57 300 000 0000").contains("Web: cima.test").contains("CIMA Agencia");
        assertThat(layout.text("Hola")).startsWith("Hola\n\n--\nCIMA Agencia")
                .contains("Correo: hola@cima.test").contains("CIMAxis");
    }

    @Test
    void sinDatosDeContactoElPieSoloLlevaLaEmpresa() {
        String texto = new EmailLayoutRenderer(null, null, null, null, null).text("Hola");

        assertThat(texto).contains("--\nCIMA\n\n").doesNotContain("Correo:");
    }
}
