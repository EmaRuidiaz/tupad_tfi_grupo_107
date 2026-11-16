package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.DetalleCompra;
import com.grupo107.agtech.model.UnidadMedida;

import java.math.BigDecimal;

public record DetalleCompraResponse(
        Long id,
        Long insumoId,
        String insumoNombre,
        UnidadMedida unidadMedida,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {
    public static DetalleCompraResponse from(DetalleCompra d) {
        return new DetalleCompraResponse(
                d.getId(),
                d.getInsumo().getId(),
                d.getInsumo().getNombre(),
                d.getInsumo().getUnidadMedida(),
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getSubtotal()
        );
    }
}