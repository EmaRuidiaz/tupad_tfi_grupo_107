package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AjusteStockRequest {

    /** Positiva para ingresos (ej. recuento) y negativa para mermas. Respeta {@code DECIMAL(12, 2)}. */
    @NotNull(message = "La cantidad del ajuste es obligatoria")
    @Digits(integer = 10, fraction = 2, message = "La cantidad del ajuste admite hasta 10 enteros y 2 decimales")
    private BigDecimal cantidad;

    @NotBlank(message = "La observación es obligatoria para un ajuste de stock")
    private String observacion;
}
