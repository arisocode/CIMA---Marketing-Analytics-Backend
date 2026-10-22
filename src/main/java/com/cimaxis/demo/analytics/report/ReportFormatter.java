package com.cimaxis.demo.analytics.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formatos de los reportes descargables (PDF y Excel), en español de Colombia.
 * Es la misma convencion que usa el frontend:
 * <ul>
 *   <li>conteos: 2 (sin decimales, punto de miles);</li>
 *   <li>dinero: "$ 4.500.000" (sin decimales);</li>
 *   <li>porcentaje: "26,67 %"; variacion: "▲ +12,5 %" / "▼ -3,0 %";</li>
 *   <li>dias: "19,0 días";</li>
 *   <li>fechas: "22 de octubre de 2026, 4:36 p. m." en America/Bogota; periodos: "Octubre 2026".</li>
 * </ul>
 * Los nombres de meses y el a. m./p. m. se escriben a mano para no depender de
 * los datos de idioma instalados en el servidor.
 */
public final class ReportFormatter {

    public static final ZoneId ZONA_CIMA = ZoneId.of("America/Bogota");
    public static final String SIN_DATO = "—";

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private static final Map<String, String> TIPOS_CAMPANA = Map.of(
            "positioning", "Posicionamiento",
            "direct_sales", "Venta directa",
            "value_content", "Contenido de valor",
            "testimonial", "Testimoniales",
            "reactivation", "Reactivación");

    private static final Map<String, String> ESTADOS_CAMPANA = Map.of(
            "draft", "Borrador",
            "active", "Activa",
            "paused", "Pausada",
            "completed", "Completada",
            "cancelled", "Cancelada");

    private static final Map<String, String> ESTADOS_PROPUESTA = Map.of(
            "in_diagnosis", "En diagnóstico",
            "sent", "Enviada",
            "in_negotiation", "En negociación",
            "approved", "Aprobada",
            "rejected", "Rechazada");

    private static final Map<String, String> ESTADOS_PROYECTO = Map.ofEntries(
            Map.entry("in_progress", "En curso"),
            Map.entry("in progress", "En curso"),
            Map.entry("in_review", "En revisión"),
            Map.entry("pending", "Pendiente"),
            Map.entry("completed", "Completado"),
            Map.entry("closed", "Cerrado"),
            Map.entry("cancelled", "Cancelado"),
            Map.entry("canceled", "Cancelado"),
            Map.entry("on_hold", "En pausa"));

    private ReportFormatter() {
    }

    // ------------------------------------------------------------------ numeros

    /** Conteo entero: 2, 1.250. null = "0". */
    public static String entero(Number valor) {
        if (valor == null) return "0";
        BigDecimal numero = valor instanceof BigDecimal b ? b : new BigDecimal(valor.toString());
        return patron("#,##0").format(numero.setScale(0, RoundingMode.HALF_UP));
    }

    /** Pesos colombianos sin decimales: "$ 4.500.000". null = "$ 0". */
    public static String moneda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String numero = patron("#,##0").format(v.abs());
        return (v.signum() < 0 ? "-$ " : "$ ") + numero;
    }

    /** Porcentaje que ya viene en escala 0-100: 26.67 -> "26,67 %". */
    public static String porcentaje(BigDecimal valorEnPorcentaje) {
        BigDecimal v = valorEnPorcentaje != null ? valorEnPorcentaje : BigDecimal.ZERO;
        return patron("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP)) + " %";
    }

    /** Dias con una cifra decimal: 19 -> "19,0 días"; 1 -> "1,0 días". */
    public static String dias(BigDecimal valor) {
        BigDecimal v = valor != null ? valor : BigDecimal.ZERO;
        return patron("#,##0.0").format(v.setScale(1, RoundingMode.HALF_UP)) + " días";
    }

    /**
     * Variacion frente al periodo anterior: "▲ +12,5 %", "▼ -3,0 %", "= 0,0 %".
     * Si el anterior es null o 0 devuelve "sin dato".
     */
    public static String variacion(BigDecimal actual, BigDecimal anterior) {
        BigDecimal delta = variacionPorcentual(actual, anterior);
        if (delta == null) return "sin dato";
        String numero = patron("#,##0.0").format(delta.abs());
        if (delta.signum() > 0) return "▲ +" + numero + " %";
        if (delta.signum() < 0) return "▼ -" + numero + " %";
        return "= " + numero + " %";
    }

    /** (actual - anterior) / |anterior| x 100 con una cifra decimal; null si el anterior es null o 0. */
    public static BigDecimal variacionPorcentual(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) return null;
        BigDecimal base = actual != null ? actual : BigDecimal.ZERO;
        return base.subtract(anterior).multiply(BigDecimal.valueOf(100))
                .divide(anterior.abs(), 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ fechas

    /** "22 de octubre de 2026". */
    public static String fecha(LocalDate fecha) {
        if (fecha == null) return SIN_DATO;
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    /** "22 de octubre de 2026, 4:36 p. m." (la fecha ya esta en hora de Bogota). */
    public static String fechaHora(LocalDateTime fechaHora) {
        if (fechaHora == null) return SIN_DATO;
        int hora = fechaHora.getHour();
        int hora12 = hora % 12 == 0 ? 12 : hora % 12;
        String minutos = String.format("%02d", fechaHora.getMinute());
        return fecha(fechaHora.toLocalDate()) + ", " + hora12 + ":" + minutos + (hora < 12 ? " a. m." : " p. m.");
    }

    /** Instante convertido a hora de Bogota. */
    public static String fechaHora(ZonedDateTime instante) {
        if (instante == null) return SIN_DATO;
        return fechaHora(instante.withZoneSameInstant(ZONA_CIMA).toLocalDateTime());
    }

    /** "2026-10" -> "Octubre 2026". Si no tiene formato YYYY-MM se devuelve tal cual. */
    public static String periodo(String periodo) {
        if (periodo == null || periodo.isBlank()) return SIN_DATO;
        try {
            YearMonth ym = YearMonth.parse(periodo.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            String mes = MESES[ym.getMonthValue() - 1];
            return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + ym.getYear();
        } catch (Exception e) {
            return periodo;
        }
    }

    /** Rango de periodos "Agosto 2026 a Octubre 2026" (o uno solo). Acepta la lista en cualquier orden. */
    public static String rangoPeriodos(List<String> periodos) {
        List<String> validos = periodos == null ? List.of()
                : periodos.stream().filter(p -> p != null && !p.isBlank()).sorted().toList();
        if (validos.isEmpty()) return "Sin períodos";
        String desde = periodo(validos.get(0));
        String hasta = periodo(validos.get(validos.size() - 1));
        return desde.equals(hasta) ? desde : desde + " a " + hasta;
    }

    // ------------------------------------------------------------------ traducciones

    public static String tipoCampana(Object tipo) {
        return traducir(tipo, TIPOS_CAMPANA);
    }

    public static String estadoCampana(Object estado) {
        return traducir(estado, ESTADOS_CAMPANA);
    }

    /**
     * Estado de cualquier entidad del reporte (campaña, propuesta o proyecto). Si no
     * se reconoce se devuelve con la primera letra en mayuscula.
     */
    public static String estado(Object estado) {
        if (estado == null) return SIN_DATO;
        String clave = clave(estado);
        if (ESTADOS_CAMPANA.containsKey(clave)) return ESTADOS_CAMPANA.get(clave);
        if (ESTADOS_PROPUESTA.containsKey(clave)) return ESTADOS_PROPUESTA.get(clave);
        if (ESTADOS_PROYECTO.containsKey(clave)) return ESTADOS_PROYECTO.get(clave);
        String texto = estado.toString().trim().replace('_', ' ');
        return texto.isEmpty() ? SIN_DATO : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Estado de un proyecto (masculino: Completado, Cerrado); si no se reconoce, como estado(). */
    public static String estadoProyecto(Object estado) {
        if (estado != null && ESTADOS_PROYECTO.containsKey(clave(estado))) return ESTADOS_PROYECTO.get(clave(estado));
        return estado(estado);
    }

    /** Recorta el texto a maximo caracteres, terminando en "…". */
    public static String recortar(String texto, int maximo) {
        if (texto == null) return "";
        String limpio = texto.strip().replaceAll("\\s+", " ");
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 1).stripTrailing() + "…";
    }

    private static String traducir(Object valor, Map<String, String> tabla) {
        if (valor == null) return SIN_DATO;
        return tabla.getOrDefault(clave(valor), valor.toString());
    }

    private static String clave(Object valor) {
        return valor.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static DecimalFormat patron(String patron) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        simbolos.setMinusSign('-');
        DecimalFormat formato = new DecimalFormat(patron, simbolos);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato;
    }
}
