package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.service.clima.ConsultaClima;
import com.grupo107.agtech.service.clima.LecturaClima;
import com.grupo107.agtech.service.clima.OrigenClima;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado de la validación climática de una labor (HU-10, CU-04): la labor actualizada,
 * la lectura usada y, si no se aprobó, qué variables incumplieron la regla del tipo de labor.
 * En una autorización manual (RN-11) no hay lectura: los campos climáticos quedan en null.
 */
public record ValidacionClimaResponse(
        boolean aprobada,
        boolean autorizacionManual,
        EstadoLabor estadoResultante,
        List<String> incumplimientos,
        String mensajeAlerta,
        BigDecimal temperaturaC,
        BigDecimal vientoVelocidadKmh,
        BigDecimal probabilidadLluviaPct,
        BigDecimal humedadRelativaPct,
        LocalDateTime fechaLectura,
        OrigenClima origen,
        Instant obtenidaEn,
        LaborResponse labor
) {
    public static ValidacionClimaResponse evaluada(LaborResponse labor, ConsultaClima consulta,
                                                   List<String> incumplimientos, String mensajeAlerta) {
        LecturaClima lectura = consulta.lectura();
        boolean aprobada = incumplimientos.isEmpty();
        return new ValidacionClimaResponse(
                aprobada,
                false,
                labor.estado(),
                incumplimientos,
                aprobada ? null : mensajeAlerta,
                lectura.temperaturaC(),
                lectura.vientoVelocidadKmh(),
                lectura.probabilidadLluviaPct(),
                lectura.humedadRelativaPct(),
                lectura.fechaLectura(),
                consulta.origen(),
                consulta.obtenidaEn(),
                labor
        );
    }

    public static ValidacionClimaResponse manual(LaborResponse labor) {
        return new ValidacionClimaResponse(true, true, labor.estado(), List.of(), null,
                null, null, null, null, null, null, null, labor);
    }
}
