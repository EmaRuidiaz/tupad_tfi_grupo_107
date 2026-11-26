package com.grupo107.agtech.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProveedorRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotBlank(message = "El CUIT es obligatorio")
    @Size(max = 20, message = "El CUIT no puede superar los 20 caracteres")
    private String cuit;

    @Size(max = 40, message = "El teléfono no puede superar los 40 caracteres")
    private String telefono;

    @Email(message = "El email no tiene un formato válido")
    @Size(max = 120, message = "El email no puede superar los 120 caracteres")
    private String email;
}
