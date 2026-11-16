package com.grupo107.agtech.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CompraRequest {

    @NotNull(message = "El proveedor es obligatorio")
    private Long proveedorId;

    @NotNull(message = "La fecha de compra es obligatoria")
    private LocalDateTime fechaCompra;

    @NotEmpty(message = "La compra debe tener al menos una línea")
    @Valid
    private List<DetalleCompraRequest> detalles = new ArrayList<>();
}