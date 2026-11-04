package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompletarLaborRequest {

    @PositiveOrZero(message = "Las horas de máquina consumidas no pueden ser negativas")
    @Digits(integer = 6, fraction = 2, message = "Las horas de máquina deben tener hasta 6 dígitos enteros y 2 decimales")
    private BigDecimal horasMaquinaConsumidas;

    @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
    private String observaciones;
}
