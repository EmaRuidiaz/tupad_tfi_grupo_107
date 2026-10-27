package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoMaquinaria;
import com.grupo107.agtech.model.Maquinaria;
import com.grupo107.agtech.model.TipoMaquinaria;

import java.math.BigDecimal;

public record MaquinariaResponse(
        Long id,
        String nombre,
        TipoMaquinaria tipo,
        String matriculaOSerie,
        BigDecimal horasUsoActuales,
        BigDecimal horasParaProximoService,
        BigDecimal horasRestantesService,
        BigDecimal consumoLitrosHora,
        BigDecimal costoOperativoHora,
        EstadoMaquinaria estado,
        boolean mantenimientoVencido
) {
    public static MaquinariaResponse from(Maquinaria m) {
        return new MaquinariaResponse(
                m.getId(),
                m.getNombre(),
                m.getTipo(),
                m.getMatriculaOSerie(),
                m.getHorasUsoActuales(),
                m.getHorasParaProximoService(),
                m.getHorasRestantesService(),
                m.getConsumoLitrosHora(),
                m.getCostoOperativoHora(),
                m.getEstado(),
                m.isMantenimientoVencido()
        );
    }
}
