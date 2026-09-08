package com.cimaxis.demo.marketing.service.notifications;

/**
 * Resultado del intento de entrega de una accion de workflow.
 */
public record DispatchResult(String channel, boolean delivered, String detail) {

    public static DispatchResult ok(String channel, String detail) {
        return new DispatchResult(channel, true, detail);
    }

    public static DispatchResult failed(String channel, String detail) {
        return new DispatchResult(channel, false, detail);
    }
}
