package com.cimaxis.demo.marketing.service.notifications;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Plantilla de correo con la marca de CIMA: cabecera roja con logo (o el nombre
 * de la empresa si no hay logo), cuerpo en parrafos y pie con datos de contacto.
 * Genera la version HTML (estilos en linea, compatible con clientes de correo)
 * y la version de texto plano.
 *
 * El cuerpo llega ya con las variables reemplazadas (MessageRenderer) y se
 * escapa antes de insertarlo en el HTML.
 */
@Component
public class EmailLayoutRenderer {

    // Colores de marca (mismos tokens del frontend: --cima-red-800, --cima-red-900, --cima-ink).
    static final String ROJO_CIMA = "#86070C";
    static final String ROJO_CIMA_OSCURO = "#680609";
    static final String TINTA = "#282829";
    static final String GRIS_TEXTO = "#626267";
    static final String GRIS_FONDO = "#F7F7F7";
    static final String FONDO_PAGINA = "#EEEEEE";
    static final String GRIS_BORDE = "#DEDEDF";

    private static final String FIRMA = "Este mensaje fue enviado desde CIMAxis, la plataforma comercial de CIMA.";

    private final String logoUrl;
    private final String companyName;
    private final String contactEmail;
    private final String contactPhone;
    private final String website;

    public EmailLayoutRenderer(@Value("${cimaxis.mail.logo-url:}") String logoUrl,
                               @Value("${cimaxis.mail.company-name:CIMA}") String companyName,
                               @Value("${cimaxis.mail.contact-email:}") String contactEmail,
                               @Value("${cimaxis.mail.contact-phone:}") String contactPhone,
                               @Value("${cimaxis.mail.website:}") String website) {
        this.logoUrl = limpio(logoUrl);
        this.companyName = limpio(companyName).isEmpty() ? "CIMA" : limpio(companyName);
        this.contactEmail = limpio(contactEmail);
        this.contactPhone = limpio(contactPhone);
        this.website = limpio(website);
    }

    /** Documento HTML completo del correo. */
    public String html(String subject, String body) {
        StringBuilder html = new StringBuilder(2048);
        html.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>").append(escapar(subject)).append("</title></head>")
            .append("<body style=\"margin:0;padding:0;background:").append(FONDO_PAGINA).append(";\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"background:").append(FONDO_PAGINA).append(";padding:24px 12px;\"><tr><td align=\"center\">")
            .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"max-width:600px;width:100%;background:#FFFFFF;border-radius:12px;overflow:hidden;")
            .append("font-family:Arial,Helvetica,sans-serif;color:").append(TINTA).append(";\">");

        // Cabecera
        html.append("<tr><td style=\"background:").append(ROJO_CIMA).append(";padding:24px 32px;\">")
            .append(cabecera())
            .append("</td></tr>")
            .append("<tr><td style=\"height:4px;line-height:4px;font-size:0;background:")
            .append(ROJO_CIMA_OSCURO).append(";\">&nbsp;</td></tr>");

        // Cuerpo
        html.append("<tr><td style=\"padding:32px;font-size:15px;line-height:1.6;color:").append(TINTA).append(";\">");
        for (String parrafo : parrafos(body)) {
            html.append("<p style=\"margin:0 0 16px;\">")
                .append(escapar(parrafo).replace("\n", "<br>"))
                .append("</p>");
        }
        html.append("</td></tr>");

        // Pie
        html.append("<tr><td style=\"padding:20px 32px;background:").append(GRIS_FONDO)
            .append(";border-top:1px solid ").append(GRIS_BORDE)
            .append(";font-size:12px;line-height:1.5;color:").append(GRIS_TEXTO).append(";\">")
            .append("<strong style=\"color:").append(TINTA).append(";\">").append(escapar(companyName)).append("</strong>");
        for (String dato : datosDeContacto()) {
            html.append("<br>").append(escapar(dato));
        }
        html.append("<br><br>").append(escapar(FIRMA))
            .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    /** Version de texto plano con el mismo contenido y pie. */
    public String text(String body) {
        StringBuilder texto = new StringBuilder();
        texto.append(body != null ? body.strip() : "").append("\n\n--\n").append(companyName);
        for (String dato : datosDeContacto()) {
            texto.append('\n').append(dato);
        }
        texto.append("\n\n").append(FIRMA);
        return texto.toString();
    }

    private String cabecera() {
        if (logoUrl.startsWith("https://") || logoUrl.startsWith("http://")) {
            return "<img src=\"" + escapar(logoUrl) + "\" alt=\"" + escapar(companyName)
                    + "\" height=\"40\" style=\"display:block;height:40px;border:0;outline:none;\">";
        }
        return "<span style=\"font-size:22px;font-weight:bold;letter-spacing:1px;color:#FFFFFF;\">"
                + escapar(companyName) + "</span>";
    }

    private List<String> datosDeContacto() {
        List<String> datos = new ArrayList<>();
        if (!contactEmail.isEmpty()) datos.add("Correo: " + contactEmail);
        if (!contactPhone.isEmpty()) datos.add("Teléfono: " + contactPhone);
        if (!website.isEmpty()) datos.add("Web: " + website);
        return datos;
    }

    /** Separa el cuerpo en parrafos por lineas en blanco. */
    private static List<String> parrafos(String body) {
        List<String> resultado = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return resultado;
        }
        for (String bloque : body.replace("\r\n", "\n").strip().split("\n\\s*\n")) {
            if (!bloque.isBlank()) {
                resultado.add(bloque.strip());
            }
        }
        return resultado;
    }

    static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
