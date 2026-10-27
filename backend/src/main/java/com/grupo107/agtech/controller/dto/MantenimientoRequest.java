package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.TipoMantenimiento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MantenimientoRequest {

    @NotNull(message = "La fecha del service es obligatoria")
    @PastOrPresent(message = "La fecha del service no puede ser futura")
    private LocalDate fechaService;

    @NotNull(message = "La lectura del horómetro es obligatoria")
    @PositiveOrZero(message = "La lectura del horómetro no puede ser negativa")
    @Digits(integer = 8, fraction = 2, message = "La lectura del horómetro admite hasta 8 enteros y 2 decimales")
    private BigDecimal horasMaquinaEnService;

    @NotNull(message = "El tipo de mantenimiento es obligatorio")
    private TipoMantenimiento tipoMantenimiento;

    @NotNull(message = "El costo es obligatorio")
    @PositiveOrZero(message = "El costo no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El costo admite hasta 8 enteros y 2 decimales")
    private BigDecimal costo;

    @NotNull(message = "Las horas del próximo service son obligatorias")
    @Positive(message = "Las horas del próximo service deben ser mayores a cero")
    @Digits(integer = 8, fraction = 2, message = "Las horas del próximo service admiten hasta 8 enteros y 2 decimales")
    private BigDecimal proximoServiceHoras;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;
}
