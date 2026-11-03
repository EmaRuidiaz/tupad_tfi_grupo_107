package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.EstadoReserva;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.Maquinaria;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.model.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LaborResponse(
        Long id,
        Long loteId,
        String loteNombre,
        BigDecimal loteSuperficieHa,
        EstadoLote loteEstado,
        Long maquinariaId,
        String maquinariaNombre,
        String maquinariaMatriculaOSerie,
        BigDecimal maquinariaCostoOperativoHora,
        Boolean maquinariaMantenimientoVencido,
        Long usuarioId,
        String usuarioUsername,
        TipoLabor tipoLabor,
        boolean requiereValidacionClimatica,
        LocalDateTime fechaPlanificada,
        LocalDateTime fechaEjecucion,
        EstadoLabor estado,
        BigDecimal horasMaquinaConsumidas,
        BigDecimal costoTotalCalculado,
        BigDecimal costoInsumosEstimado,
        BigDecimal costoInsumosConsumido,
        BigDecimal costoMaquinaria,
        BigDecimal temperaturaRegistrada,
        BigDecimal vientoVelocidadKmh,
        BigDecimal probabilidadLluviaPct,
        BigDecimal humedadRegistradaPct,
        String observacionesClimaticas,
        List<LaborInsumoResponse> insumos
) {
    public static LaborResponse from(LaborAgricola labor) {
        Lote lote = labor.getLote();
        Maquinaria maq = labor.getMaquinaria();
        Usuario usr = labor.getUsuario();
        List<LaborInsumoResponse> items = labor.getInsumos().stream()
                .map(LaborInsumoResponse::from)
                .toList();
        BigDecimal costoInsumosEstimado = items.stream()
                .filter(i -> i.estadoReserva() != EstadoReserva.LIBERADO)
                .map(LaborInsumoResponse::costoEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Subtotales históricos fijados al consumir (RN-05): no dependen del precio vigente.
        BigDecimal costoInsumosConsumido = items.stream()
                .filter(i -> i.estadoReserva() == EstadoReserva.CONSUMIDO)
                .map(LaborInsumoResponse::costoSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Se deriva del total inmutable para no depender del costo/hora vigente de la máquina.
        BigDecimal costoMaquinaria = labor.getEstado() == EstadoLabor.COMPLETADA
                && labor.getCostoTotalCalculado() != null
                ? labor.getCostoTotalCalculado().subtract(costoInsumosConsumido).max(BigDecimal.ZERO)
                : null;

        return new LaborResponse(
                labor.getId(),
                lote != null ? lote.getId() : null,
                lote != null ? lote.getNombre() : null,
                lote != null ? lote.getSuperficieHa() : null,
                lote != null ? lote.getEstado() : null,
                maq != null ? maq.getId() : null,
                maq != null ? maq.getNombre() : null,
                maq != null ? maq.getMatriculaOSerie() : null,
                maq != null ? maq.getCostoOperativoHora() : null,
                maq != null ? maq.isMantenimientoVencido() : null,
                usr != null ? usr.getId() : null,
                usr != null ? usr.getUsername() : null,
                labor.getTipoLabor(),
                labor.getTipoLabor() != null && labor.getTipoLabor().requiereValidacionClimatica(),
                labor.getFechaPlanificada(),
                labor.getFechaEjecucion(),
                labor.getEstado(),
                labor.getHorasMaquinaConsumidas(),
                labor.getCostoTotalCalculado(),
                costoInsumosEstimado,
                costoInsumosConsumido,
                costoMaquinaria,
                labor.getTemperaturaRegistrada(),
                labor.getVientoVelocidadKmh(),
                labor.getProbabilidadLluviaPct(),
                labor.getHumedadRegistradaPct(),
                labor.getObservacionesClimaticas(),
                items
        );
    }
}
