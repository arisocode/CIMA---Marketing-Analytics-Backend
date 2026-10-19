package com.cimaxis.demo.marketing.dto.notifications;

/** Cupo diario de correos (zona America/Bogota). remaining nunca es negativo. */
public record EmailQuotaResponse(boolean enabled, int dailyLimit, long sentToday, long remaining) {
}
