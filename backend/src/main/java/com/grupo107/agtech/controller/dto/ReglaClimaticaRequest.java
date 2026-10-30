package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.TipoLabor;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReglaClimaticaRequest {

    @NotNull(message = "El tipo de labor es obligatorio")
    private TipoLabor tipoLabor;

    @NotNull(message = "La velocidad máxima de viento es obligatoria")
    @Positive(message = "La velocidad máxima de viento debe ser mayor a 0")
    @Digits(integer = 3, fraction = 2, message = "La velocidad máxima de viento admite hasta 3 enteros y 2 decimales")
    private BigDecimal vientoMaxKmh;

    @NotNull(message = "La velocidad mínima de viento es obligatoria")
    @PositiveOrZero(message = "La velocidad mínima de viento no puede ser negativa")
    @Digits(integer = 3, fraction = 2, message = "La velocidad mínima de viento admite hasta 3 enteros y 2 decimales")
    private BigDecimal vientoMinKmh;

    @NotNull(message = "La temperatura mínima es obligatoria")
    @DecimalMin(value = "-50.00", message = "La temperatura mínima no puede ser menor a -50 °C")
    @DecimalMax(value = "60.00", message = "La temperatura mínima no puede superar los 60 °C")
    @Digits(integer = 3, fraction = 2, message = "La temperatura mínima admite hasta 3 enteros y 2 decimales")
    private BigDecimal tempMinC;

    @NotNull(message = "La temperatura máxima es obligatoria")
    @DecimalMin(value = "-50.00", message = "La temperatura máxima no puede ser menor a -50 °C")
    @DecimalMax(value = "60.00", message = "La temperatura máxima no puede superar los 60 °C")
    @Digits(integer = 3, fraction = 2, message = "La temperatura máxima admite hasta 3 enteros y 2 decimales")
    private BigDecimal tempMaxC;

    @NotNull(message = "La probabilidad máxima de lluvia es obligatoria")
    @DecimalMin(value = "0.00", message = "La probabilidad máxima de lluvia debe estar entre 0 y 100")
    @DecimalMax(value = "100.00", message = "La probabilidad máxima de lluvia debe estar entre 0 y 100")
    @Digits(integer = 3, fraction = 2, message = "La probabilidad máxima de lluvia admite hasta 3 enteros y 2 decimales")
    private BigDecimal probLluviaMaxPct;

    @NotNull(message = "La humedad relativa mínima es obligatoria")
    @DecimalMin(value = "0.00", message = "La humedad relativa mínima debe estar entre 0 y 100")
    @DecimalMax(value = "100.00", message = "La humedad relativa mínima debe estar entre 0 y 100")
    @Digits(integer = 3, fraction = 2, message = "La humedad relativa mínima admite hasta 3 enteros y 2 decimales")
    private BigDecimal humedadMinPct;

    @NotBlank(message = "El mensaje de alerta es obligatorio")
    @Size(max = 255, message = "El mensaje de alerta no puede superar los 255 caracteres")
    private String mensajeAlerta;
}
