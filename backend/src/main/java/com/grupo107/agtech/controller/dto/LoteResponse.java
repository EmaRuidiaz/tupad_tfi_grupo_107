package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.Lote;

import java.math.BigDecimal;
import java.util.Set;

public record LoteResponse(
        Long id,
        String nombre,
        BigDecimal superficieHa,
        BigDecimal latitud,
        BigDecimal longitud,
        String poligonoWkt,
        String tipoSuelo,
        EstadoLote estado,
        Set<EstadoLote> transicionesPermitidas,
        String observaciones,
        String usuarioUsername
) {
    public static LoteResponse from(Lote lote) {
        return new LoteResponse(
                lote.getId(),
                lote.getNombre(),
                lote.getSuperficieHa(),
                lote.getLatitud(),
                lote.getLongitud(),
                lote.getPoligonoWkt(),
                lote.getTipoSuelo(),
                lote.getEstado(),
                lote.getEstado().transicionesManuales(),
                lote.getObservaciones(),
                lote.getUsuario() != null ? lote.getUsuario().getUsername() : null
        );
    }
}
