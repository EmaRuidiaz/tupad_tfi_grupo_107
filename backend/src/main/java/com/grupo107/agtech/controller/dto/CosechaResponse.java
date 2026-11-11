package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.RegistroCosecha;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cosecha registrada con sus valores calculados (HU-12). {@code costosCicloImputados} no se persiste:
 * se deriva de {@code ingresoBrutoTotal - margenNetoCalculado}, ambos fijados al registrar (RN-05).
 */
public record CosechaResponse(
        Long id,
        Long loteId,
        String loteNombre,
        BigDecimal superficieHa,
        Long cultivoId,
        String cultivoNombre,
        String cultivoEspecie,
        Long laborId,
        LocalDate fechaCosecha,
        BigDecimal toneladasTotales,
        BigDecimal rindeTonPorHa,
        BigDecimal precioVentaPorTon,
        BigDecimal ingresoBrutoTotal,
        BigDecimal costosCicloImputados,
        BigDecimal margenNetoCalculado,
        BigDecimal roiPct,
        String usuarioUsername
) {
    public static CosechaResponse from(RegistroCosecha c) {
        BigDecimal costos = c.getIngresoBrutoTotal().subtract(c.getMargenNetoCalculado());
        return new CosechaResponse(
                c.getId(),
                c.getLote().getId(),
                c.getLote().getNombre(),
                c.getLote().getSuperficieHa(),
                c.getCultivo().getId(),
                c.getCultivo().getNombre(),
                c.getCultivo().getEspecie(),
                c.getLabor() != null ? c.getLabor().getId() : null,
                c.getFechaCosecha(),
                c.getToneladasTotales(),
                c.getRindeTonPorHa(),
                c.getPrecioVentaPorTon(),
                c.getIngresoBrutoTotal(),
                costos,
                c.getMargenNetoCalculado(),
                c.calcularRoi(),
                c.getUsuario() != null ? c.getUsuario().getUsername() : null
        );
    }
}
