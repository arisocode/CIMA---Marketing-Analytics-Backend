package com.cimaxis.demo.marketing.service.templates;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cimaxis.demo.analytics.domain.Client;

/**
 * Reemplazo de variables en mensajes y plantillas. Lo usan los workflows, la
 * vista previa de plantillas y el correo de prueba, para que el texto que se
 * previsualiza sea el mismo que se envia.
 *
 * Variables: {nombre} (nombre del cliente), {plan} (su plan comercial),
 * {empresa} (empresa del cliente segun el CRM; si no hay, su nombre) y
 * {workflow} (nombre de la automatizacion). Una variable desconocida se deja
 * tal cual.
 */
public final class MessageRenderer {

    public static final String NOMBRE = "nombre";
    public static final String PLAN = "plan";
    public static final String EMPRESA = "empresa";
    public static final String WORKFLOW = "workflow";

    private static final String NOMBRE_POR_DEFECTO = "Cliente";

    private MessageRenderer() {
    }

    /** Reemplaza cada {clave} por su valor. Valores nulos se reemplazan por texto vacio. */
    public static String render(String plantilla, Map<String, String> valores) {
        if (plantilla == null) {
            return null;
        }
        String resultado = plantilla;
        if (valores != null) {
            for (Map.Entry<String, String> variable : valores.entrySet()) {
                String valor = variable.getValue() != null ? variable.getValue() : "";
                resultado = resultado.replace("{" + variable.getKey() + "}", valor);
            }
        }
        return resultado;
    }

    /** Variables de un cliente con sus valores por defecto. */
    public static Map<String, String> variables(String nombre, String plan, String empresa) {
        String nombreFinal = tieneTexto(nombre) ? nombre.trim() : NOMBRE_POR_DEFECTO;
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put(NOMBRE, nombreFinal);
        valores.put(PLAN, tieneTexto(plan) ? plan.trim() : "");
        valores.put(EMPRESA, tieneTexto(empresa) ? empresa.trim() : nombreFinal);
        return valores;
    }

    /** Variables a partir del cliente sincronizado en marketing (sin consultar el CRM). */
    public static Map<String, String> variables(Client client) {
        String nombre = nombreCliente(client);
        return variables(nombre, client != null ? client.getPlan() : null, nombre);
    }

    /** Nombre visible: el sincronizado del CRM, luego el correo y por ultimo el id. */
    public static String nombreCliente(Client client) {
        if (client == null) {
            return NOMBRE_POR_DEFECTO;
        }
        if (tieneTexto(client.getAdditionalInfo())) {
            return client.getAdditionalInfo().trim();
        }
        if (tieneTexto(client.getContactInfo())) {
            return client.getContactInfo().trim();
        }
        return tieneTexto(client.getClientId()) ? client.getClientId() : NOMBRE_POR_DEFECTO;
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
