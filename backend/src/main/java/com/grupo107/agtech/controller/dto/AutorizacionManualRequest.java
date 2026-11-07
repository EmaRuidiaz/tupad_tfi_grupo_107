package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AutorizacionManualRequest {

    @NotBlank(message = "La justificación de la autorización manual es obligatoria (RN-11)")
    @Size(min = 10, max = 500, message = "La justificación debe tener entre 10 y 500 caracteres")
    private String justificacion;
}
