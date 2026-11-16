package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.Proveedor;

public record ProveedorResponse(
        Long id,
        String nombre,
        String cuit,
        String telefono,
        String email
) {
    public static ProveedorResponse from(Proveedor p) {
        return new ProveedorResponse(
                p.getId(),
                p.getNombre(),
                p.getCuit(),
                p.getTelefono(),
                p.getEmail()
        );
    }
}