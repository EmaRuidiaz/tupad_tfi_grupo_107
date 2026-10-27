package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoLote;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambioEstadoLoteRequest {

    @NotNull(message = "El estado destino es obligatorio")
    private EstadoLote estado;

    // Obligatoria cuando el cambio representa una pérdida total del cultivo.
    private String observacion;
}
