package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.CompraInsumo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CompraResponse(
        Long id,
        Long proveedorId,
        String proveedorNombre,
        String proveedorCuit,
        Long usuarioId,
        String usuarioUsername,
        LocalDateTime fechaCompra,
        BigDecimal totalCompra,
        List<DetalleCompraResponse> detalles
) {
    public static CompraResponse from(CompraInsumo c) {
        return new CompraResponse(
                c.getId(),
                c.getProveedor().getId(),
                c.getProveedor().getNombre(),
                c.getProveedor().getCuit(),
                c.getUsuario().getId(),
                c.getUsuario().getUsername(),
                c.getFechaCompra(),
                c.getTotalCompra(),
                c.getDetalles().stream().map(DetalleCompraResponse::from).toList()
        );
    }
}