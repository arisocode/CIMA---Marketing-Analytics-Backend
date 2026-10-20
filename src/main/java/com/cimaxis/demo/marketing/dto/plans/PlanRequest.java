package com.cimaxis.demo.marketing.dto.plans;

/** Datos para crear o editar un plan. color en hexadecimal (#RRGGBB o #RGB). */
public record PlanRequest(String name, String color, String description, Integer sortOrder) {
}
