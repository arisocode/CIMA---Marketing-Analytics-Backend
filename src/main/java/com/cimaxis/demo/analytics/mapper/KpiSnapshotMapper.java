package com.cimaxis.demo.analytics.mapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cimaxis.demo.analytics.domain.KpiSnapshot;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;

/**
 * Traduccion entre la entidad KpiSnapshot y su DTO de salida.
 */
@Component
public class KpiSnapshotMapper {

    /** Convierte una entidad en DTO. Devuelve null si la entidad es null. */
    public KpiSnapshotDto toDto(KpiSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return KpiSnapshotDto.builder()
                .snapshotsId(snapshot.getSnapshotsId())
                .period(snapshot.getPeriod())
                .calculatedAt(snapshot.getCalculatedAt())
                .newClients(snapshot.getNewClients())
                .closedProjects(snapshot.getClosedProjects())
                .estimatedRevenue(snapshot.getEstimatedRevenue())
                .activeCampaigns(snapshot.getActiveCampaigns())
                .clientsContacted(snapshot.getClientsContacted())
                .responseRate(snapshot.getResponseRate())
                .avgCloseDays(snapshot.getAvgCloseDays())
                .projectsInProgress(snapshot.getProjectsInProgress())
                .calculatedBy(snapshot.getCalculatedBy())
                .build();
    }

    /** Convierte una lista de entidades. Tolera null y listas vacias. */
    public List<KpiSnapshotDto> toDtoList(List<KpiSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        return snapshots.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Copia los valores calculados sobre una entidad ya gestionada por JPA.
     */
    public void copiarValores(KpiSnapshot origen, KpiSnapshot destino) {
        destino.setPeriod(origen.getPeriod());
        destino.setNewClients(origen.getNewClients());
        destino.setClosedProjects(origen.getClosedProjects());
        destino.setEstimatedRevenue(origen.getEstimatedRevenue());
        destino.setActiveCampaigns(origen.getActiveCampaigns());
        destino.setClientsContacted(origen.getClientsContacted());
        destino.setResponseRate(origen.getResponseRate());
        destino.setAvgCloseDays(origen.getAvgCloseDays());
        destino.setProjectsInProgress(origen.getProjectsInProgress());
    }
}
