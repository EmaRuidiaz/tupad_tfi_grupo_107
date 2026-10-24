package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.MovimientoStock;
import com.grupo107.agtech.model.TipoMovimientoStock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoStockResponse(
        Long id,
        Long insumoId,
        String insumoNombre,
        LocalDateTime fecha,
        TipoMovimientoStock tipo,
        BigDecimal cantidad,
        Long usuarioId,
        String usuarioUsername,
        Long laborId,
        Long compraId,
        String observacion
) {
    public static MovimientoStockResponse from(MovimientoStock m) {
        return new MovimientoStockResponse(
                m.getId(),
                m.getInsumo().getId(),
                m.getInsumo().getNombre(),
                m.getFecha(),
                m.getTipo(),
                m.getCantidad(),
                m.getUsuario().getId(),
                m.getUsuario().getUsername(),
                m.getLaborId(),
                m.getCompraId(),
                m.getObservacion()
        );
    }
}
