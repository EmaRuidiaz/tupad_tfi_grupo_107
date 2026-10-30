package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.CategoriaInsumo;
import com.grupo107.agtech.model.EstadoReserva;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.LaborInsumo;
import com.grupo107.agtech.model.UnidadMedida;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record LaborInsumoResponse(
        Long id,
        Long insumoId,
        String insumoNombre,
        CategoriaInsumo insumoCategoria,
        UnidadMedida insumoUnidadMedida,
        BigDecimal insumoPrecioUnitario,
        BigDecimal cantidadUtilizada,
        BigDecimal costoSubtotal,
        BigDecimal costoEstimado,
        EstadoReserva estadoReserva
) {
    public static LaborInsumoResponse from(LaborInsumo item) {
        Insumo insumo = item.getInsumo();
        BigDecimal precio = insumo != null ? insumo.getPrecioUnitario() : BigDecimal.ZERO;
        BigDecimal estimado = item.getEstadoReserva() == EstadoReserva.CONSUMIDO
                ? item.getCostoSubtotal()
                : item.getCantidadUtilizada().multiply(precio).setScale(2, RoundingMode.HALF_UP);
        return new LaborInsumoResponse(
                item.getId(),
                insumo != null ? insumo.getId() : null,
                insumo != null ? insumo.getNombre() : null,
                insumo != null ? insumo.getCategoria() : null,
                insumo != null ? insumo.getUnidadMedida() : null,
                precio,
                item.getCantidadUtilizada(),
                item.getCostoSubtotal(),
                estimado,
                item.getEstadoReserva()
        );
    }
}
