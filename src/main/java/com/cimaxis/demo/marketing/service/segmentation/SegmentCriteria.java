package com.cimaxis.demo.marketing.service.segmentation;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Criterios de segmentacion de clientes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SegmentCriteria {

    /** Planes contratados: Oro, Esmeralda, Premium. */
    private List<String> plans;

    /** true = solo clientes con al menos un proyecto; false = solo sin proyectos. */
    private Boolean hasProjects;

    /** true = solo clientes ya contactados por alguna campana. */
    private Boolean hasInteractions;

    /** Clientes sin contacto durante al menos N dias. */
    private Integer minDaysWithoutContact;

    /** Clientes con al menos una propuesta en alguno de estos estados. */
    private List<String> proposalStatuses;
}
