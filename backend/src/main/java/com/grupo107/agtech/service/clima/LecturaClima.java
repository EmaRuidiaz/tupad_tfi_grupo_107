package com.grupo107.agtech.service.clima;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Condiciones climáticas actuales en un punto. Usa las mismas variables y unidades que
 * el motor de reglas agronómicas (HU-07) para poder evaluarlas directamente.
 *
 * @param fechaLectura hora local del lugar informada por el proveedor
 */
public record LecturaClima(
        BigDecimal temperaturaC,
        BigDecimal vientoVelocidadKmh,
        BigDecimal probabilidadLluviaPct,
        BigDecimal humedadRelativaPct,
        BigDecimal precipitacionMm,
        LocalDateTime fechaLectura
) {
}
