package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.ReporteFinancieroLoteResponse;
import com.grupo107.agtech.controller.dto.TableroFinancieroResponse;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.service.TableroFinancieroService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tablero-financiero")
@PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
public class TableroFinancieroController {

    private final TableroFinancieroService tableroFinancieroService;

    public TableroFinancieroController(TableroFinancieroService tableroFinancieroService) {
        this.tableroFinancieroService = tableroFinancieroService;
    }

    @GetMapping
    public TableroFinancieroResponse obtenerTablero(
            @RequestParam(required = false) EstadoLote estadoLote,
            @RequestParam(required = false) Boolean soloConCosecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String alcanceCiclo) {
        return tableroFinancieroService.obtenerTablero(
                estadoLote, soloConCosecha, fechaDesde, fechaHasta, alcanceCiclo
        );
    }

    @GetMapping("/lotes")
    public List<ReporteFinancieroLoteResponse> listarPorLote(
            @RequestParam(required = false) EstadoLote estadoLote,
            @RequestParam(required = false) Boolean soloConCosecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String alcanceCiclo) {
        return tableroFinancieroService.listarReportesPorLote(
                estadoLote, soloConCosecha, fechaDesde, fechaHasta, alcanceCiclo
        );
    }

    @GetMapping("/lotes/{loteId}")
    public ReporteFinancieroLoteResponse obtenerPorLote(
            @PathVariable Long loteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String alcanceCiclo) {
        return tableroFinancieroService.obtenerReportePorLote(loteId, fechaDesde, fechaHasta, alcanceCiclo);
    }
}
