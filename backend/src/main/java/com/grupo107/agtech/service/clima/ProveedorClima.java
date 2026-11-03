package com.grupo107.agtech.service.clima;

import java.math.BigDecimal;

/**
 * Puerto hacia el proveedor meteorológico (doc 07, sección 7.1). La lógica de negocio depende
 * de esta interfaz y no de la API concreta, así el proveedor puede cambiarse sin tocarla.
 */
public interface ProveedorClima {

    /**
     * Devuelve las condiciones actuales en las coordenadas indicadas.
     *
     * @throws ClimaNoDisponibleException si la API falla y no hay una lectura en caché vigente
     */
    ConsultaClima obtenerClimaActual(BigDecimal latitud, BigDecimal longitud);
}
