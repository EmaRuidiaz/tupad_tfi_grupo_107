package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.CategoriaInsumo;
import com.grupo107.agtech.model.UnidadMedida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InsumoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotNull(message = "La categoría es obligatoria")
    private CategoriaInsumo categoria;

    @NotNull(message = "La unidad de medida es obligatoria")
    private UnidadMedida unidadMedida;

    /**
     * Stock físico inicial al dar de alta el insumo (opcional, por defecto 0).
     * Si es mayor a 0, el servicio genera automáticamente un movimiento AJUSTE ("Stock inicial").
     */
    @PositiveOrZero(message = "El stock inicial no puede ser negativo")
    private BigDecimal stockInicial;

    @NotNull(message = "El stock mínimo de alerta es obligatorio")
    @PositiveOrZero(message = "El stock mínimo de alerta no puede ser negativo")
    private BigDecimal stockMinimoAlerta;

    @NotNull(message = "El precio unitario es obligatorio")
    @PositiveOrZero(message = "El precio unitario no puede ser negativo")
    private BigDecimal precioUnitario;
}
