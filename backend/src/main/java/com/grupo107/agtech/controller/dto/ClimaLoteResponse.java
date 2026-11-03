package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.service.clima.ConsultaClima;
import com.grupo107.agtech.service.clima.LecturaClima;
import com.grupo107.agtech.service.clima.OrigenClima;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record ClimaLoteResponse(
        Long loteId,
        String loteNombre,
        BigDecimal latitud,
        BigDecimal longitud,
        BigDecimal temperaturaC,
        BigDecimal vientoVelocidadKmh,
        BigDecimal probabilidadLluviaPct,
        BigDecimal humedadRelativaPct,
        BigDecimal precipitacionMm,
        LocalDateTime fechaLectura,
        OrigenClima origen,
        Instant obtenidaEn
) {
    public static ClimaLoteResponse from(Lote lote, ConsultaClima consulta) {
        LecturaClima lectura = consulta.lectura();
        return new ClimaLoteResponse(
                lote.getId(),
                lote.getNombre(),
                lote.getLatitud(),
                lote.getLongitud(),
                lectura.temperaturaC(),
                lectura.vientoVelocidadKmh(),
                lectura.probabilidadLluviaPct(),
                lectura.humedadRelativaPct(),
                lectura.precipitacionMm(),
                lectura.fechaLectura(),
                consulta.origen(),
                consulta.obtenidaEn()
        );
    }
}
