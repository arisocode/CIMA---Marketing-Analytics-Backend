package com.cimaxis.demo.marketing.service.notifications;

/**
 * Resultado de un envio de correo (o de un canal simulado).
 *
 * QUEUED: crm-media lo acepto (HTTP 202) y lo encolo para enviarlo por SMTP.
 * FAILED: no se pudo entregar a crm-media (sin correo, error HTTP o de red).
 * SIMULATED: el envio real esta apagado (cimaxis.mail.enabled=false) o el canal no envia.
 * SKIPPED_LIMIT: se alcanzo el limite diario de correos; no se llamo a crm-media.
 */
public enum DeliveryStatus {
    QUEUED, FAILED, SIMULATED, SKIPPED_LIMIT
}
