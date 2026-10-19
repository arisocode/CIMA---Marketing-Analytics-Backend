package com.cimaxis.demo.analytics.dto;

/**
 * Proyeccion agregada JPA para evitar consultas N+1 en resumenes de actividad de clientes.
 */
public interface ClientActivitySummaryProjection {
    String getClientId();
    String getPlan();
    long getCampaignCount();
    long getProjectCount();
}
