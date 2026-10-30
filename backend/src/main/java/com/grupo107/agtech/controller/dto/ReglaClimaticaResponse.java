package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.ReglaClimatica;
import com.grupo107.agtech.model.TipoLabor;

import java.math.BigDecimal;

public record ReglaClimaticaResponse(
        Long id,
        TipoLabor tipoLabor,
        BigDecimal vientoMinKmh,
        BigDecimal vientoMaxKmh,
        BigDecimal tempMinC,
        BigDecimal tempMaxC,
        BigDecimal probLluviaMaxPct,
        BigDecimal humedadMinPct,
        String mensajeAlerta
) {
    public static ReglaClimaticaResponse from(ReglaClimatica r) {
        return new ReglaClimaticaResponse(
                r.getId(),
                r.getTipoLabor(),
                r.getVientoMinKmh(),
                r.getVientoMaxKmh(),
                r.getTempMinC(),
                r.getTempMaxC(),
                r.getProbLluviaMaxPct(),
                r.getHumedadMinPct(),
                r.getMensajeAlerta()
        );
    }
}
