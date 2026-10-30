package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluacionClimaRequest {

    @NotNull(message = "La velocidad del viento es obligatoria")
    @PositiveOrZero(message = "La velocidad del viento no puede ser negativa")
    @Digits(integer = 4, fraction = 2, message = "La velocidad del viento admite hasta 4 enteros y 2 decimales")
    private BigDecimal vientoVelocidadKmh;

    @NotNull(message = "La temperatura es obligatoria")
    @DecimalMin(value = "-50.00", message = "La temperatura no puede ser menor a -50 °C")
    @DecimalMax(value = "60.00", message = "La temperatura no puede superar los 60 °C")
    @Digits(integer = 3, fraction = 2, message = "La temperatura admite hasta 3 enteros y 2 decimales")
    private BigDecimal temperaturaC;

    @NotNull(message = "La probabilidad de lluvia es obligatoria")
    @DecimalMin(value = "0.00", message = "La probabilidad de lluvia debe estar entre 0 y 100")
    @DecimalMax(value = "100.00", message = "La probabilidad de lluvia debe estar entre 0 y 100")
    @Digits(integer = 3, fraction = 2, message = "La probabilidad de lluvia admite hasta 3 enteros y 2 decimales")
    private BigDecimal probabilidadLluviaPct;

    @NotNull(message = "La humedad relativa es obligatoria")
    @DecimalMin(value = "0.00", message = "La humedad relativa debe estar entre 0 y 100")
    @DecimalMax(value = "100.00", message = "La humedad relativa debe estar entre 0 y 100")
    @Digits(integer = 3, fraction = 2, message = "La humedad relativa admite hasta 3 enteros y 2 decimales")
    private BigDecimal humedadRelativaPct;
}
