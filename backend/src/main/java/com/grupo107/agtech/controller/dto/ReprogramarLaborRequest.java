package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReprogramarLaborRequest {

    @NotNull(message = "La nueva fecha planificada es obligatoria")
    private LocalDateTime fechaPlanificada;

    @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
    private String observaciones;
}
