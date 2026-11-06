package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.RegistroCosecha;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CosechaResumenResponse {

    private Long cosechaId;
    private Long laborId;
    private Long cultivoId;
    private String cultivoNombre;
    private String cultivoEspecie;
    private LocalDate fechaCosecha;
    private BigDecimal toneladasTotales;
    private BigDecimal rindeTonPorHa;
    private BigDecimal precioVentaPorTon;
    private BigDecimal ingresoBrutoTotal;
    private BigDecimal margenNetoCalculado;
    private String usuarioUsername;

    public static CosechaResumenResponse from(RegistroCosecha cosecha) {
        return new CosechaResumenResponse(
                cosecha.getId(),
                cosecha.getLabor() != null ? cosecha.getLabor().getId() : null,
                cosecha.getCultivo() != null ? cosecha.getCultivo().getId() : null,
                cosecha.getCultivo() != null ? cosecha.getCultivo().getNombre() : null,
                cosecha.getCultivo() != null ? cosecha.getCultivo().getEspecie() : null,
                cosecha.getFechaCosecha(),
                cosecha.getToneladasTotales(),
                cosecha.getRindeTonPorHa(),
                cosecha.getPrecioVentaPorTon(),
                cosecha.getIngresoBrutoTotal(),
                cosecha.getMargenNetoCalculado(),
                cosecha.getUsuario() != null ? cosecha.getUsuario().getUsername() : null
        );
    }
}
