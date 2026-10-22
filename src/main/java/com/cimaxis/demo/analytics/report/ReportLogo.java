package com.cimaxis.demo.analytics.report;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Logo de la marca para el encabezado de los PDF, leido de cimaxis.mail.logo-url
 * (la misma URL que usan los correos). Se descarga una vez y queda en memoria;
 * si la URL no esta configurada o falla, el PDF muestra el texto "CIMA" y se
 * reintenta pasados 10 minutos. Nunca bloquea la generacion mas de unos segundos.
 */
@Component
public class ReportLogo {

    private static final Logger log = LoggerFactory.getLogger(ReportLogo.class);
    private static final int TIEMPO_MAXIMO_MS = 3000;
    private static final int TAMANO_MAXIMO = 2 * 1024 * 1024;
    private static final Duration REINTENTO = Duration.ofMinutes(10);

    private final String url;
    private volatile byte[] cache;
    private volatile Instant fallo;

    public ReportLogo(@Value("${cimaxis.mail.logo-url:}") String url) {
        this.url = url == null ? "" : url.trim();
    }

    /** Bytes de la imagen, o null si no hay logo disponible. */
    public byte[] bytes() {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null;
        if (cache != null) return cache;
        if (fallo != null && Instant.now().isBefore(fallo.plus(REINTENTO))) return null;
        try {
            HttpURLConnection conexion = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conexion.setConnectTimeout(TIEMPO_MAXIMO_MS);
            conexion.setReadTimeout(TIEMPO_MAXIMO_MS);
            if (conexion.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conexion.getResponseCode());
            try (InputStream in = conexion.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int leidos;
                while ((leidos = in.read(buffer)) != -1) {
                    out.write(buffer, 0, leidos);
                    if (out.size() > TAMANO_MAXIMO) throw new IllegalStateException("logo de más de 2 MB");
                }
                cache = out.toByteArray();
                return cache;
            }
        } catch (Exception e) {
            fallo = Instant.now();
            log.warn("No se pudo cargar el logo de los reportes ({}): {}", url, e.getMessage());
            return null;
        }
    }
}
