package com.cimaxis.demo.analytics.dto;

import com.cimaxis.demo.analytics.domain.Client;

/**
 * Proyeccion agregada JPA para evitar consultas N+1 en resumenes de actividad de clientes.
 */
public interface ClientActivitySummaryProjection {
    String getClientId();
    Client.Plan getPlan();
    long getCampaignCount();
    long getProjectCount();
}
