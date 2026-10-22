package com.cimaxis.demo.analytics.service;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum KpiType {
    NEW_CLIENTS("new_clients", "Clientes nuevos"),
    CLOSED_PROJECTS("closed_projects", "Proyectos cerrados"),
    REVENUE("revenue", "Ingresos estimados"),
    ACTIVE_CAMPAIGNS("active_campaigns", "Campañas activas"),
    CLIENTS_CONTACTED("clients_contacted", "Clientes contactados"),
    RESPONSE_RATE("response_rate", "Tasa de respuesta"),
    AVG_CLOSE_DAYS("avg_close_days", "Días promedio de cierre"),
    PROJECTS_IN_PROGRESS("projects_in_progress", "Proyectos en curso");

    private final String key;
    private final String label;

    KpiType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static KpiType fromKey(String value) {
        if (value != null) {
            for (KpiType type : values()) {
                if (type.key.equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("KPI invalido: " + value + ". Valores permitidos: "
                + Arrays.stream(values()).map(KpiType::key).collect(Collectors.joining(", ")));
    }
}
