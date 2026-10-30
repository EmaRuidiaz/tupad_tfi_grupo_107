package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.RegistroMantenimiento;
import com.grupo107.agtech.model.TipoMantenimiento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MantenimientoResponse(
        Long id,
        Long maquinariaId,
        LocalDate fechaService,
        BigDecimal horasMaquinaEnService,
        TipoMantenimiento tipoMantenimiento,
        BigDecimal costo,
        BigDecimal proximoServiceHoras,
        String descripcion,
        String usuarioUsername
) {
    public static MantenimientoResponse from(RegistroMantenimiento r) {
        return new MantenimientoResponse(
                r.getId(),
                r.getMaquinaria().getId(),
                r.getFechaService(),
                r.getHorasMaquinaEnService(),
                r.getTipoMantenimiento(),
                r.getCosto(),
                r.getProximoServiceHoras(),
                r.getDescripcion(),
                r.getUsuario() != null ? r.getUsuario().getUsername() : null
        );
    }
}
