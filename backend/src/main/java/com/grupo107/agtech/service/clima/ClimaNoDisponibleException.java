package com.grupo107.agtech.service.clima;

/** La API climática no respondió y no hay lectura en caché vigente (doc 07, sección 7.3, paso 3). */
public class ClimaNoDisponibleException extends RuntimeException {

    public ClimaNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
