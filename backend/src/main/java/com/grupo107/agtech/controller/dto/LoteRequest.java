package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotNull(message = "La superficie es obligatoria")
    @Positive(message = "La superficie debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "La superficie admite hasta 8 enteros y 2 decimales")
    private BigDecimal superficieHa;

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    @Digits(integer = 3, fraction = 6, message = "La latitud admite hasta 6 decimales")
    private BigDecimal latitud;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    @Digits(integer = 3, fraction = 6, message = "La longitud admite hasta 6 decimales")
    private BigDecimal longitud;

    private String poligonoWkt;

    @Size(max = 50, message = "El tipo de suelo no puede superar los 50 caracteres")
    private String tipoSuelo;

    private String observaciones;
}
