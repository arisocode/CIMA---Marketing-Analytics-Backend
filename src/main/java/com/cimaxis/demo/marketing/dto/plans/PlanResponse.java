package com.cimaxis.demo.marketing.dto.plans;

import java.util.UUID;

/** Plan con el numero de clientes que lo tienen asignado. */
public record PlanResponse(
        UUID planId,
        String name,
        String color,
        String description,
        Integer sortOrder,
        Boolean active,
        long clientCount) {
}
