package com.grupo107.agtech.service.clima;

import java.time.Instant;

/**
 * Resultado de consultar el clima: la lectura, de dónde salió y cuándo se obtuvo de la API.
 * Si el origen es {@link OrigenClima#CACHE}, {@code obtenidaEn} indica la antigüedad del dato.
 */
public record ConsultaClima(LecturaClima lectura, OrigenClima origen, Instant obtenidaEn) {
}
