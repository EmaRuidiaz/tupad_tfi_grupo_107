package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.TipoMaquinaria;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaquinariaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotNull(message = "El tipo de maquinaria es obligatorio")
    private TipoMaquinaria tipo;

    @NotBlank(message = "La matrícula o número de serie es obligatorio")
    @Size(max = 50, message = "La matrícula o serie no puede superar los 50 caracteres")
    private String matriculaOSerie;

    @PositiveOrZero(message = "Las horas de uso actuales no pueden ser negativas")
    @Digits(integer = 8, fraction = 2, message = "Las horas de uso admiten hasta 8 enteros y 2 decimales")
    private BigDecimal horasUsoActuales;

    @NotNull(message = "Las horas para el próximo service son obligatorias")
    @Positive(message = "Las horas para el próximo service deben ser mayores a cero")
    @Digits(integer = 8, fraction = 2, message = "Las horas para el próximo service admiten hasta 8 enteros y 2 decimales")
    private BigDecimal horasParaProximoService;

    @NotNull(message = "El consumo en litros por hora es obligatorio")
    @PositiveOrZero(message = "El consumo por hora no puede ser negativo")
    @Digits(integer = 6, fraction = 2, message = "El consumo por hora admite hasta 6 enteros y 2 decimales")
    private BigDecimal consumoLitrosHora;

    @NotNull(message = "El costo operativo por hora es obligatorio")
    @PositiveOrZero(message = "El costo operativo por hora no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El costo operativo por hora admite hasta 8 enteros y 2 decimales")
    private BigDecimal costoOperativoHora;
}
