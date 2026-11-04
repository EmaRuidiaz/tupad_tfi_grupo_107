package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoReserva;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.model.LaborInsumo;
import com.grupo107.agtech.model.TipoLabor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostoLaborResumenResponse {

    private Long laborId;
    private TipoLabor tipoLabor;
    private LocalDateTime fechaPlanificada;
    private LocalDateTime fechaEjecucion;
    private String maquinariaNombre;
    private BigDecimal horasMaquinaConsumidas;
    private BigDecimal costoInsumos;
    private BigDecimal costoMaquinaria;
    private BigDecimal costoTotalCalculado;
    private String usuarioUsername;

    public static CostoLaborResumenResponse from(LaborAgricola labor) {
        BigDecimal totalConsolidado = labor.getCostoTotalCalculado() != null
                ? labor.getCostoTotalCalculado().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal insumos = labor.getInsumos() == null ? BigDecimal.ZERO : labor.getInsumos().stream()
                .filter(i -> i.getEstadoReserva() == EstadoReserva.CONSUMIDO)
                .map(i -> i.getCostoSubtotal() != null ? i.getCostoSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal maquinaria = totalConsolidado.subtract(insumos).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal horas = labor.getHorasMaquinaConsumidas() != null
                ? labor.getHorasMaquinaConsumidas().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new CostoLaborResumenResponse(
                labor.getId(),
                labor.getTipoLabor(),
                labor.getFechaPlanificada(),
                labor.getFechaEjecucion(),
                labor.getMaquinaria() != null ? labor.getMaquinaria().getNombre() : null,
                horas,
                insumos,
                maquinaria,
                totalConsolidado,
                labor.getUsuario() != null ? labor.getUsuario().getUsername() : null
        );
    }
}
