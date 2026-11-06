package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.CosechaResumenResponse;
import com.grupo107.agtech.controller.dto.CostoLaborResumenResponse;
import com.grupo107.agtech.controller.dto.ReporteFinancieroLoteResponse;
import com.grupo107.agtech.controller.dto.TableroFinancieroResponse;
import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.RegistroCosecha;
import com.grupo107.agtech.repository.LaborAgricolaRepository;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.repository.RegistroCosechaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TableroFinancieroService {

    public static final String ESTADO_ROI_CALCULADO = "CALCULADO";
    public static final String ESTADO_ROI_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_ROI_SIN_COSTOS = "SIN_COSTOS";

    public static final String ALCANCE_HISTORICO = "HISTORICO";
    public static final String ALCANCE_ULTIMO_CICLO = "ULTIMO_CICLO";

    private static final Set<EstadoLabor> ESTADOS_LABOR_PENDIENTES = EnumSet.of(
            EstadoLabor.PLANIFICADA,
            EstadoLabor.APROBADA_CLIMA,
            EstadoLabor.BLOQUEADA_CLIMA,
            EstadoLabor.EN_EJECUCION
    );

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final LoteRepository loteRepository;
    private final LaborAgricolaRepository laborRepository;
    private final RegistroCosechaRepository cosechaRepository;

    public TableroFinancieroService(LoteRepository loteRepository,
                                    LaborAgricolaRepository laborRepository,
                                    RegistroCosechaRepository cosechaRepository) {
        this.loteRepository = loteRepository;
        this.laborRepository = laborRepository;
        this.cosechaRepository = cosechaRepository;
    }

    @Transactional(readOnly = true)
    public TableroFinancieroResponse obtenerTablero(EstadoLote estadoLote, Boolean soloConCosecha) {
        return obtenerTablero(estadoLote, soloConCosecha, null, null, null);
    }

    @Transactional(readOnly = true)
    public TableroFinancieroResponse obtenerTablero(EstadoLote estadoLote,
                                                    Boolean soloConCosecha,
                                                    LocalDate fechaDesde,
                                                    LocalDate fechaHasta,
                                                    String alcanceCiclo) {
        List<ReporteFinancieroLoteResponse> lotes = listarReportesPorLote(
                estadoLote, soloConCosecha, fechaDesde, fechaHasta, alcanceCiclo
        );

        int totalLotes = lotes.size();
        int lotesConCosecha = (int) lotes.stream().filter(ReporteFinancieroLoteResponse::isCosechaRegistrada).count();
        int lotesSinCosecha = totalLotes - lotesConCosecha;

        BigDecimal superficieTotalHa = lotes.stream()
                .map(ReporteFinancieroLoteResponse::getSuperficieHa)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal superficieCosechadaHa = lotes.stream()
                .filter(ReporteFinancieroLoteResponse::isCosechaRegistrada)
                .map(ReporteFinancieroLoteResponse::getSuperficieHa)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        int totalLaboresCompletadas = lotes.stream()
                .mapToInt(ReporteFinancieroLoteResponse::getCantidadLaboresCompletadas)
                .sum();

        BigDecimal costosTotalesAcumulados = lotes.stream()
                .map(ReporteFinancieroLoteResponse::getCostosTotalesAcumulados)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoInsumosTotal = lotes.stream()
                .map(ReporteFinancieroLoteResponse::getCostoInsumosAcumulado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoMaquinariaTotal = lotes.stream()
                .map(ReporteFinancieroLoteResponse::getCostoMaquinariaAcumulado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoPromedioPorHa = superficieTotalHa.compareTo(BigDecimal.ZERO) > 0
                ? costosTotalesAcumulados.divide(superficieTotalHa, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal costosLotesConCosecha = lotes.stream()
                .filter(ReporteFinancieroLoteResponse::isCosechaRegistrada)
                .map(ReporteFinancieroLoteResponse::getCostosTotalesAcumulados)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal ingresosBrutosTotales = lotes.stream()
                .filter(ReporteFinancieroLoteResponse::isCosechaRegistrada)
                .map(r -> r.getIngresoBrutoTotal() != null ? r.getIngresoBrutoTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal toneladasTotalesCosechadas = lotes.stream()
                .filter(ReporteFinancieroLoteResponse::isCosechaRegistrada)
                .map(r -> r.getToneladasTotales() != null ? r.getToneladasTotales() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal rindePromedioTonPorHa = superficieCosechadaHa.compareTo(BigDecimal.ZERO) > 0
                ? toneladasTotalesCosechadas.divide(superficieCosechadaHa, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal margenNetoTotal = ingresosBrutosTotales.subtract(costosTotalesAcumulados)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal margenNetoLotesCosechados = ingresosBrutosTotales.subtract(costosLotesConCosecha)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal roiGlobalPct = (lotesConCosecha > 0 && costosTotalesAcumulados.compareTo(BigDecimal.ZERO) > 0)
                ? margenNetoTotal.multiply(CIEN).divide(costosTotalesAcumulados, 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal roiLotesCosechadosPct = (lotesConCosecha > 0 && costosLotesConCosecha.compareTo(BigDecimal.ZERO) > 0)
                ? margenNetoLotesCosechados.multiply(CIEN).divide(costosLotesConCosecha, 2, RoundingMode.HALF_UP)
                : null;

        return new TableroFinancieroResponse(
                totalLotes,
                lotesConCosecha,
                lotesSinCosecha,
                superficieTotalHa,
                superficieCosechadaHa,
                totalLaboresCompletadas,
                costosTotalesAcumulados,
                costoInsumosTotal,
                costoMaquinariaTotal,
                costoPromedioPorHa,
                costosLotesConCosecha,
                ingresosBrutosTotales,
                toneladasTotalesCosechadas,
                rindePromedioTonPorHa,
                margenNetoTotal,
                margenNetoLotesCosechados,
                roiGlobalPct,
                roiLotesCosechadosPct,
                lotes
        );
    }

    @Transactional(readOnly = true)
    public List<ReporteFinancieroLoteResponse> listarReportesPorLote(EstadoLote estadoLote, Boolean soloConCosecha) {
        return listarReportesPorLote(estadoLote, soloConCosecha, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ReporteFinancieroLoteResponse> listarReportesPorLote(EstadoLote estadoLote,
                                                                     Boolean soloConCosecha,
                                                                     LocalDate fechaDesde,
                                                                     LocalDate fechaHasta,
                                                                     String alcanceCiclo) {
        validarRangoFechas(fechaDesde, fechaHasta);

        List<Lote> lotes = estadoLote != null
                ? loteRepository.findByEstadoOrderByIdAsc(estadoLote)
                : loteRepository.findAllByOrderByIdAsc();

        if (lotes.isEmpty()) {
            return List.of();
        }

        // Carga masiva en 2 consultas adicionales para evitar N+1 y cumplir RNF-02 (< 2s con 500 lotes).
        Map<Long, List<LaborAgricola>> laboresPorLote = laborRepository.findAllByOrderByFechaPlanificadaDescIdDesc()
                .stream()
                .filter(l -> l.getLote() != null)
                .collect(Collectors.groupingBy(l -> l.getLote().getId()));

        Map<Long, List<RegistroCosecha>> cosechasPorLote = cosechaRepository.findAllByOrderByFechaCosechaDescIdDesc()
                .stream()
                .filter(c -> c.getLote() != null)
                .collect(Collectors.groupingBy(c -> c.getLote().getId()));

        return lotes.stream()
                .map(lote -> construirReporteLote(
                        lote,
                        laboresPorLote.getOrDefault(lote.getId(), List.of()),
                        cosechasPorLote.getOrDefault(lote.getId(), List.of()),
                        fechaDesde,
                        fechaHasta,
                        alcanceCiclo
                ))
                .filter(r -> !Boolean.TRUE.equals(soloConCosecha) || r.isCosechaRegistrada())
                .toList();
    }

    @Transactional(readOnly = true)
    public ReporteFinancieroLoteResponse obtenerReportePorLote(Long loteId) {
        return obtenerReportePorLote(loteId, null, null, null);
    }

    @Transactional(readOnly = true)
    public ReporteFinancieroLoteResponse obtenerReportePorLote(Long loteId,
                                                               LocalDate fechaDesde,
                                                               LocalDate fechaHasta,
                                                               String alcanceCiclo) {
        validarRangoFechas(fechaDesde, fechaHasta);

        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + loteId));

        List<LaborAgricola> labores = laborRepository.findByLoteIdOrderByFechaPlanificadaDescIdDesc(loteId);
        List<RegistroCosecha> cosechas = cosechaRepository.findByLoteIdOrderByFechaCosechaDescIdDesc(loteId);

        return construirReporteLote(lote, labores, cosechas, fechaDesde, fechaHasta, alcanceCiclo);
    }

    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fechaDesde (" + fechaDesde + ") no puede ser posterior a fechaHasta (" + fechaHasta + ")");
        }
    }

    private LocalDate obtenerFechaEfectivaLabor(LaborAgricola labor) {
        LocalDateTime fechaBase = labor.getFechaEjecucion() != null
                ? labor.getFechaEjecucion()
                : labor.getFechaPlanificada();
        return fechaBase != null ? fechaBase.toLocalDate() : null;
    }

    private boolean dentroDeRango(LocalDate fecha, LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fecha == null) {
            return fechaDesde == null && fechaHasta == null;
        }
        if (fechaDesde != null && fecha.isBefore(fechaDesde)) {
            return false;
        }
        if (fechaHasta != null && fecha.isAfter(fechaHasta)) {
            return false;
        }
        return true;
    }

    private ReporteFinancieroLoteResponse construirReporteLote(
            Lote lote,
            List<LaborAgricola> laboresDelLote,
            List<RegistroCosecha> cosechasDelLote,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String alcanceCiclo) {

        BigDecimal superficieHa = lote.getSuperficieHa() != null
                ? lote.getSuperficieHa().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 1. Filtrado por rango explícito de fechas (si fue solicitado)
        List<RegistroCosecha> cosechasEnRango = cosechasDelLote.stream()
                .filter(c -> dentroDeRango(c.getFechaCosecha(), fechaDesde, fechaHasta))
                .toList();

        List<LaborAgricola> laboresCompletadasEnRango = laboresDelLote.stream()
                .filter(l -> l.getEstado() == EstadoLabor.COMPLETADA)
                .filter(l -> dentroDeRango(obtenerFechaEfectivaLabor(l), fechaDesde, fechaHasta))
                .toList();

        // 2. Delimitación por ventana de ciclo productivo (RN-12: ULTIMO_CICLO vs HISTORICO)
        boolean modoUltimoCiclo = ALCANCE_ULTIMO_CICLO.equalsIgnoreCase(alcanceCiclo);
        List<RegistroCosecha> cosechasEvaluadas = cosechasEnRango;
        List<LaborAgricola> laboresEvaluadas = laboresCompletadasEnRango;

        if (modoUltimoCiclo && !cosechasEnRango.isEmpty()) {
            RegistroCosecha ultimaCosecha = cosechasEnRango.get(0);
            LocalDate fechaCierreCiclo = ultimaCosecha.getFechaCosecha();
            LocalDate fechaInicioExclusiva = cosechasEnRango.size() > 1
                    ? cosechasEnRango.get(1).getFechaCosecha()
                    : null;

            // Si el lote ya reingresó a una nueva campaña activa posterior a la última cosecha
            // y existen labores completadas posteriores a esa cosecha, evaluamos la campaña en curso.
            List<LaborAgricola> laboresCampanaEnCurso = fechaCierreCiclo != null
                    ? laboresCompletadasEnRango.stream()
                            .filter(l -> {
                                LocalDate f = obtenerFechaEfectivaLabor(l);
                                return f != null && f.isAfter(fechaCierreCiclo);
                            })
                            .toList()
                    : List.of();

            if (lote.getEstado() != EstadoLote.COSECHADO && !laboresCampanaEnCurso.isEmpty()) {
                cosechasEvaluadas = List.of();
                laboresEvaluadas = laboresCampanaEnCurso;
            } else {
                cosechasEvaluadas = List.of(ultimaCosecha);
                laboresEvaluadas = laboresCompletadasEnRango.stream()
                        .filter(l -> {
                            LocalDate f = obtenerFechaEfectivaLabor(l);
                            if (f == null) return true;
                            boolean despuesDeAnterior = fechaInicioExclusiva == null || f.isAfter(fechaInicioExclusiva);
                            boolean hastaCierre = fechaCierreCiclo == null || !f.isAfter(fechaCierreCiclo);
                            return despuesDeAnterior && hastaCierre;
                        })
                        .toList();
            }
        }

        List<CostoLaborResumenResponse> laboresCompletadas = laboresEvaluadas.stream()
                .map(CostoLaborResumenResponse::from)
                .toList();

        int cantidadPendientes = (int) laboresDelLote.stream()
                .filter(l -> ESTADOS_LABOR_PENDIENTES.contains(l.getEstado()))
                .count();

        BigDecimal horasMaquinaTotales = laboresCompletadas.stream()
                .map(CostoLaborResumenResponse::getHorasMaquinaConsumidas)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoInsumosAcumulado = laboresCompletadas.stream()
                .map(CostoLaborResumenResponse::getCostoInsumos)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costosTotalesAcumulados = laboresCompletadas.stream()
                .map(CostoLaborResumenResponse::getCostoTotalCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoMaquinariaAcumulado = costosTotalesAcumulados.subtract(costoInsumosAcumulado)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal costoPorHa = superficieHa.compareTo(BigDecimal.ZERO) > 0
                ? costosTotalesAcumulados.divide(superficieHa, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<CosechaResumenResponse> cosechas = cosechasEvaluadas.stream()
                .map(CosechaResumenResponse::from)
                .toList();

        boolean cosechaRegistrada = !cosechas.isEmpty();
        boolean tieneCostos = costosTotalesAcumulados.compareTo(BigDecimal.ZERO) > 0;

        String cultivoNombre = null;
        String cultivoEspecie = null;
        LocalDate fechaUltimaCosecha = null;
        BigDecimal toneladasTotales = null;
        BigDecimal rindeTonPorHa = null;
        BigDecimal precioVentaPromedioPorTon = null;
        BigDecimal ingresoBrutoTotal = null;
        BigDecimal ingresoPorHa = null;
        BigDecimal margenNeto = null;
        BigDecimal margenPorHa = null;
        BigDecimal roiPct = null;
        String estadoRoi;
        String mensajeRoi;

        if (cosechaRegistrada) {
            CosechaResumenResponse ultima = cosechas.get(0);
            cultivoNombre = ultima.getCultivoNombre();
            cultivoEspecie = ultima.getCultivoEspecie();
            fechaUltimaCosecha = ultima.getFechaCosecha();

            toneladasTotales = cosechas.stream()
                    .map(c -> c.getToneladasTotales() != null ? c.getToneladasTotales() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            rindeTonPorHa = superficieHa.compareTo(BigDecimal.ZERO) > 0
                    ? toneladasTotales.divide(superficieHa, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            ingresoBrutoTotal = cosechas.stream()
                    .map(c -> c.getIngresoBrutoTotal() != null ? c.getIngresoBrutoTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            precioVentaPromedioPorTon = toneladasTotales.compareTo(BigDecimal.ZERO) > 0
                    ? ingresoBrutoTotal.divide(toneladasTotales, 2, RoundingMode.HALF_UP)
                    : ultima.getPrecioVentaPorTon();

            ingresoPorHa = superficieHa.compareTo(BigDecimal.ZERO) > 0
                    ? ingresoBrutoTotal.divide(superficieHa, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            // Módulo 9 / HU-13: Margen Neto = Ingreso Bruto - Costos Totales Acumulados
            margenNeto = ingresoBrutoTotal.subtract(costosTotalesAcumulados)
                    .setScale(2, RoundingMode.HALF_UP);

            margenPorHa = superficieHa.compareTo(BigDecimal.ZERO) > 0
                    ? margenNeto.divide(superficieHa, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (tieneCostos) {
                // RF-08 criterio 1: ROI (%) = (Margen Neto / Costos Totales Acumulados) * 100
                roiPct = margenNeto.multiply(CIEN)
                        .divide(costosTotalesAcumulados, 2, RoundingMode.HALF_UP);
                estadoRoi = ESTADO_ROI_CALCULADO;
                mensajeRoi = roiPct.toPlainString() + " %";
            } else {
                // RF-08 criterio 3: costos acumulados igual a 0 -> se evita división por cero
                estadoRoi = ESTADO_ROI_SIN_COSTOS;
                mensajeRoi = "Sin costos registrados";
            }
        } else {
            // RF-08 criterios 2 y 3: lote sin cosecha registrada
            if (tieneCostos) {
                estadoRoi = ESTADO_ROI_PENDIENTE;
                mensajeRoi = "Pendiente de cosecha";
            } else {
                estadoRoi = ESTADO_ROI_SIN_COSTOS;
                mensajeRoi = "Pendiente (sin costos registrados)";
            }
        }

        return new ReporteFinancieroLoteResponse(
                lote.getId(),
                lote.getNombre(),
                superficieHa,
                lote.getEstado(),
                lote.getTipoSuelo(),
                laboresCompletadas.size(),
                cantidadPendientes,
                horasMaquinaTotales,
                costoInsumosAcumulado,
                costoMaquinariaAcumulado,
                costosTotalesAcumulados,
                costoPorHa,
                cosechaRegistrada,
                cosechas.size(),
                cultivoNombre,
                cultivoEspecie,
                fechaUltimaCosecha,
                toneladasTotales,
                rindeTonPorHa,
                precioVentaPromedioPorTon,
                ingresoBrutoTotal,
                ingresoPorHa,
                margenNeto,
                margenPorHa,
                roiPct,
                estadoRoi,
                mensajeRoi,
                laboresCompletadas,
                cosechas
        );
    }
}
