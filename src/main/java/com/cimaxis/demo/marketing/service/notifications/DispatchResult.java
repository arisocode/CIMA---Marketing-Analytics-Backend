package com.cimaxis.demo.marketing.service.notifications;

/**
 * Resultado del intento de entrega de una accion de workflow.
 *
 * deliveryStatus es null para acciones que no envian nada (por ejemplo
 * log_followup). delivered=true cuando la accion cuenta como realizada
 * (QUEUED, SIMULATED o seguimiento interno).
 */
public record DispatchResult(String channel, boolean delivered, String detail, DeliveryStatus deliveryStatus) {

    public DispatchResult(String channel, boolean delivered, String detail) {
        this(channel, delivered, detail, null);
    }

    public static DispatchResult ok(String channel, String detail) {
        return new DispatchResult(channel, true, detail);
    }

    public static DispatchResult failed(String channel, String detail) {
        return new DispatchResult(channel, false, detail);
    }

    public static DispatchResult queued(String channel, String detail) {
        return new DispatchResult(channel, true, detail, DeliveryStatus.QUEUED);
    }

    public static DispatchResult simulated(String channel, String detail) {
        return new DispatchResult(channel, true, detail, DeliveryStatus.SIMULATED);
    }

    public static DispatchResult deliveryFailed(String channel, String detail) {
        return new DispatchResult(channel, false, detail, DeliveryStatus.FAILED);
    }

    public static DispatchResult skippedByLimit(String channel, String detail) {
        return new DispatchResult(channel, false, detail, DeliveryStatus.SKIPPED_LIMIT);
    }

    /** Mismo resultado con otro canal (por ejemplo admin_notification). */
    public DispatchResult withChannel(String otroCanal) {
        return new DispatchResult(otroCanal, delivered, detail, deliveryStatus);
    }

    public boolean skippedByLimit() {
        return deliveryStatus == DeliveryStatus.SKIPPED_LIMIT;
    }
}
