package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de la cosecha consolidada que cierra el ciclo productivo de un lote (HU-12, CU-05).
 * Los campos calculados (rinde, ingreso bruto y margen neto) no se reciben: los calcula el servicio.
 */
@Data
public class CosechaRequest {

    @NotNull(message = "El lote es obligatorio")
    @Positive(message = "El id del lote debe ser positivo")
    private Long loteId;

    @NotNull(message = "El cultivo es obligatorio")
    @Positive(message = "El id del cultivo debe ser positivo")
    private Long cultivoId;

    /** Opcional: orden de trabajo de COSECHA (trilla) que originó el registro. */
    @Positive(message = "El id de la labor debe ser positivo")
    private Long laborId;

    @NotNull(message = "La fecha de cosecha es obligatoria")
    @PastOrPresent(message = "La fecha de cosecha no puede ser futura")
    private LocalDate fechaCosecha;

    @NotNull(message = "Las toneladas totales son obligatorias")
    @PositiveOrZero(message = "Las toneladas totales no pueden ser negativas")
    @Digits(integer = 8, fraction = 2, message = "Las toneladas totales admiten hasta 8 enteros y 2 decimales")
    private BigDecimal toneladasTotales;

    @NotNull(message = "El precio de venta por tonelada es obligatorio")
    @PositiveOrZero(message = "El precio de venta por tonelada no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio de venta por tonelada admite hasta 10 enteros y 2 decimales")
    private BigDecimal precioVentaPorTon;
}
