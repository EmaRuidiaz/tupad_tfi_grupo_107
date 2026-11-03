package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LaborInsumoRequest {

    @NotNull(message = "El insumo es obligatorio")
    private Long insumoId;

    @NotNull(message = "La cantidad del insumo es obligatoria")
    @DecimalMin(value = "0.01", message = "La cantidad del insumo debe ser mayor a 0")
    @Digits(integer = 10, fraction = 2, message = "La cantidad del insumo admite hasta 10 enteros y 2 decimales")
    private BigDecimal cantidadUtilizada;
}
