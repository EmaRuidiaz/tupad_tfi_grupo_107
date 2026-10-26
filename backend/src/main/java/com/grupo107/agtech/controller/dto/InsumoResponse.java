package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.CategoriaInsumo;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.UnidadMedida;

import java.math.BigDecimal;

public record InsumoResponse(
        Long id,
        String nombre,
        CategoriaInsumo categoria,
        UnidadMedida unidadMedida,
        BigDecimal stockActual,
        BigDecimal stockReservado,
        BigDecimal stockDisponible,
        BigDecimal stockMinimoAlerta,
        BigDecimal precioUnitario,
        boolean stockCritico
) {
    public static InsumoResponse from(Insumo insumo) {
        return new InsumoResponse(
                insumo.getId(),
                insumo.getNombre(),
                insumo.getCategoria(),
                insumo.getUnidadMedida(),
                insumo.getStockActual(),
                insumo.getStockReservado(),
                insumo.getStockDisponible(),
                insumo.getStockMinimoAlerta(),
                insumo.getPrecioUnitario(),
                insumo.isStockCritico()
        );
    }
}
