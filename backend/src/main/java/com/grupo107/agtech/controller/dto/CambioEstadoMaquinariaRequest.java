package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoMaquinaria;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambioEstadoMaquinariaRequest {

    @NotNull(message = "El nuevo estado es obligatorio")
    private EstadoMaquinaria estado;
}
