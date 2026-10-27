package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.ReglaClimatica;
import com.grupo107.agtech.model.TipoLabor;

import java.util.List;

public record EvaluacionClimaResponse(
        Long reglaId,
        TipoLabor tipoLabor,
        boolean aprobada,
        String estadoLaborSugerido,
        List<String> incumplimientos,
        String mensajeAlerta
) {
    public static EvaluacionClimaResponse from(ReglaClimatica regla, List<String> incumplimientos) {
        boolean aprobada = incumplimientos.isEmpty();
        return new EvaluacionClimaResponse(
                regla.getId(),
                regla.getTipoLabor(),
                aprobada,
                aprobada ? "APROBADA_CLIMA" : "BLOQUEADA_CLIMA",
                incumplimientos,
                aprobada ? null : regla.getMensajeAlerta()
        );
    }
}
