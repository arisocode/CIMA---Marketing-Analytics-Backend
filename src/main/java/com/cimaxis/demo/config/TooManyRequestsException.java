package com.cimaxis.demo.config;

/** Se lanza cuando un usuario supera un limite de uso; responde 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String mensaje) {
        super(mensaje);
    }
}
