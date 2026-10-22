package com.cimaxis.demo.marketing.service.segments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/**
 * Convierte SegmentCriteria a JSON y de vuelta para guardarlo en
 * marketing_segments.criteria. El formato es plano y conocido
 * ({"plans":[...],"hasProjects":true,"hasInteractions":false,
 * "minDaysWithoutContact":15,"proposalStatuses":[...]}), asi que se escribe y lee
 * aqui mismo, sin depender de la configuracion de Jackson. Campos desconocidos
 * se ignoran.
 */
public final class SegmentCriteriaCodec {

    private SegmentCriteriaCodec() {
    }

    public static String toJson(SegmentCriteria c) {
        SegmentCriteria criterio = c != null ? c : new SegmentCriteria();
        StringBuilder sb = new StringBuilder("{");
        List<String> partes = new ArrayList<>();
        if (criterio.getPlans() != null) partes.add("\"plans\":" + lista(criterio.getPlans()));
        if (criterio.getHasProjects() != null) partes.add("\"hasProjects\":" + criterio.getHasProjects());
        if (criterio.getHasInteractions() != null) partes.add("\"hasInteractions\":" + criterio.getHasInteractions());
        if (criterio.getMinDaysWithoutContact() != null) {
            partes.add("\"minDaysWithoutContact\":" + criterio.getMinDaysWithoutContact());
        }
        if (criterio.getProposalStatuses() != null) {
            partes.add("\"proposalStatuses\":" + lista(criterio.getProposalStatuses()));
        }
        sb.append(String.join(",", partes)).append('}');
        return sb.toString();
    }

    public static SegmentCriteria fromJson(String json) {
        SegmentCriteria criterio = new SegmentCriteria();
        if (json == null || json.isBlank()) return criterio;
        Object valor = new Lector(json).leerValor();
        if (!(valor instanceof Map<?, ?> mapa)) {
            throw new IllegalArgumentException("Criterio de segmento inválido: se esperaba un objeto JSON");
        }
        criterio.setPlans(textos(mapa.get("plans")));
        criterio.setHasProjects(booleano(mapa.get("hasProjects")));
        criterio.setHasInteractions(booleano(mapa.get("hasInteractions")));
        criterio.setMinDaysWithoutContact(entero(mapa.get("minDaysWithoutContact")));
        criterio.setProposalStatuses(textos(mapa.get("proposalStatuses")));
        return criterio;
    }

    private static String lista(List<String> valores) {
        List<String> partes = new ArrayList<>();
        for (String v : valores) partes.add(v == null ? "null" : texto(v));
        return "[" + String.join(",", partes) + "]";
    }

    private static String texto(String v) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : v.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static List<String> textos(Object valor) {
        if (!(valor instanceof List<?> lista)) return null;
        List<String> resultado = new ArrayList<>();
        for (Object o : lista) if (o != null) resultado.add(o.toString());
        return resultado;
    }

    private static Boolean booleano(Object valor) {
        return valor instanceof Boolean b ? b : null;
    }

    private static Integer entero(Object valor) {
        return valor instanceof Number n ? n.intValue() : null;
    }

    /** Lector JSON minimo: objetos, listas, textos, numeros, true/false/null. */
    private static final class Lector {
        private final String s;
        private int i;

        Lector(String s) {
            this.s = s;
        }

        Object leerValor() {
            espacios();
            if (i >= s.length()) throw error();
            char c = s.charAt(i);
            if (c == '{') return objeto();
            if (c == '[') return arreglo();
            if (c == '"') return cadena();
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return numero();
        }

        private Map<String, Object> objeto() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            i++;
            espacios();
            if (s.charAt(i) == '}') { i++; return mapa; }
            while (true) {
                espacios();
                String clave = cadena();
                espacios();
                if (s.charAt(i++) != ':') throw error();
                mapa.put(clave, leerValor());
                espacios();
                char c = s.charAt(i++);
                if (c == '}') return mapa;
                if (c != ',') throw error();
            }
        }

        private List<Object> arreglo() {
            List<Object> lista = new ArrayList<>();
            i++;
            espacios();
            if (s.charAt(i) == ']') { i++; return lista; }
            while (true) {
                lista.add(leerValor());
                espacios();
                char c = s.charAt(i++);
                if (c == ']') return lista;
                if (c != ',') throw error();
            }
        }

        private String cadena() {
            if (s.charAt(i) != '"') throw error();
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Number numero() {
            int inicio = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String n = s.substring(inicio, i);
            if (n.isEmpty()) throw error();
            return n.contains(".") || n.contains("e") || n.contains("E") ? Double.parseDouble(n) : Long.parseLong(n);
        }

        private void espacios() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private IllegalArgumentException error() {
            return new IllegalArgumentException("Criterio de segmento inválido (JSON mal formado)");
        }
    }
}
