package com.cimaxis.demo.config;

/**
 * Se lanza cuando un recurso solicitado por id no existe.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }

    public static ResourceNotFoundException de(String recurso, Object id) {
        return new ResourceNotFoundException(recurso + " no encontrado: " + id);
    }
}
