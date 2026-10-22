package com.cimaxis.demo.analytics.report;

import java.awt.Color;

import com.cimaxis.demo.marketing.service.notifications.EmailLayoutRenderer;

/** Colores de marca CIMA para los reportes: los mismos de EmailLayoutRenderer (y del frontend). */
public final class ReportBranding {

    public static final Color ROJO = color(EmailLayoutRenderer.ROJO_CIMA);
    public static final Color ROJO_OSCURO = color(EmailLayoutRenderer.ROJO_CIMA_OSCURO);
    public static final Color TINTA = color(EmailLayoutRenderer.TINTA);
    public static final Color GRIS_TEXTO = color(EmailLayoutRenderer.GRIS_TEXTO);
    public static final Color GRIS_FONDO = color(EmailLayoutRenderer.GRIS_FONDO);
    public static final Color GRIS_BORDE = color(EmailLayoutRenderer.GRIS_BORDE);
    public static final Color VERDE = new Color(0x15, 0x80, 0x3D);
    public static final Color BLANCO = Color.WHITE;

    private ReportBranding() {
    }

    public static Color color(String hex) {
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }

    /** RGB en bytes, para los colores de Apache POI. */
    public static byte[] rgb(Color color) {
        return new byte[] {(byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()};
    }
}
