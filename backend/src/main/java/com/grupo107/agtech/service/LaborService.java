package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.CompletarLaborRequest;
import com.grupo107.agtech.controller.dto.LaborInsumoRequest;
import com.grupo107.agtech.controller.dto.LaborRequest;
import com.grupo107.agtech.controller.dto.LaborResponse;
import com.grupo107.agtech.controller.dto.ReprogramarLaborRequest;
import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.EstadoMaquinaria;
import com.grupo107.agtech.model.EstadoReserva;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.model.LaborInsumo;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.Maquinaria;
import com.grupo107.agtech.model.MovimientoStock;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.model.TipoMovimientoStock;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.InsumoRepository;
import com.grupo107.agtech.repository.LaborAgricolaRepository;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.repository.MaquinariaRepository;
import com.grupo107.agtech.repository.MovimientoStockRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LaborService {

    private static final Set<EstadoLabor> ESTADOS_PENDIENTES = EnumSet.of(
            EstadoLabor.PLANIFICADA,
            EstadoLabor.APROBADA_CLIMA,
            EstadoLabor.BLOQUEADA_CLIMA,
            EstadoLabor.EN_EJECUCION
    );

    private static final long VENTANA_CONFLICTO_MINUTOS = 119;

    private static final int HORAS_POR_DIA = 24;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LaborAgricolaRepository laborRepository;
    private final LoteRepository loteRepository;
    private final MaquinariaRepository maquinariaRepository;
    private final InsumoRepository insumoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;

    public LaborService(LaborAgricolaRepository laborRepository,
                        LoteRepository loteRepository,
                        MaquinariaRepository maquinariaRepository,
                        InsumoRepository insumoRepository,
                        MovimientoStockRepository movimientoStockRepository,
                        UsuarioRepository usuarioRepository) {
        this.laborRepository = laborRepository;
        this.loteRepository = loteRepository;
        this.maquinariaRepository = maquinariaRepository;
        this.insumoRepository = insumoRepository;
        this.movimientoStockRepository = movimientoStockRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<LaborResponse> listar(Long loteId, EstadoLabor estado, TipoLabor tipoLabor) {
        return laborRepository.findAllByOrderByFechaPlanificadaDescIdDesc().stream()
                .filter(l -> loteId == null || (l.getLote() != null && loteId.equals(l.getLote().getId())))
                .filter(l -> estado == null || l.getEstado() == estado)
                .filter(l -> tipoLabor == null || l.getTipoLabor() == tipoLabor)
                .map(LaborResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LaborResponse obtenerPorId(Long id) {
        return LaborResponse.from(buscarLabor(id));
    }

    @Transactional
    public LaborResponse programar(LaborRequest request, String username) {
        Lote lote = buscarYValidarLote(request.getLoteId(), request.getTipoLabor());
        Maquinaria maquinaria = buscarYValidarMaquinaria(
                request.getMaquinariaId(),
                request.getFechaPlanificada(),
                null
        );

        List<LaborInsumoRequest> itemsSolicitados = validarItemsSinDuplicados(request.getInsumos());
        Map<Long, Insumo> insumosBloqueados = bloquearYValidarStockDisponible(itemsSolicitados);

        LaborAgricola labor = new LaborAgricola();
        labor.setLote(lote);
        labor.setMaquinaria(maquinaria);
        labor.setUsuario(buscarUsuario(username));
        labor.setTipoLabor(request.getTipoLabor());
        labor.setFechaPlanificada(request.getFechaPlanificada());
        labor.setEstado(EstadoLabor.PLANIFICADA);
        labor.setHorasMaquinaConsumidas(BigDecimal.ZERO);
        labor.setCostoTotalCalculado(BigDecimal.ZERO);
        labor.setObservacionesClimaticas(textoOpcional(request.getObservaciones()));

        reservarInsumosEnLabor(labor, itemsSolicitados, insumosBloqueados);

        return LaborResponse.from(laborRepository.save(labor));
    }

    @Transactional
    public LaborResponse actualizar(Long id, LaborRequest request) {
        LaborAgricola labor = buscarLaborParaActualizar(id);

        if (!labor.getEstado().admiteReprogramacionOCancelacion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede editar una labor en estado " + labor.getEstado());
        }

        List<LaborInsumoRequest> itemsSolicitados = validarItemsSinDuplicados(request.getInsumos());
        Lote lote = buscarYValidarLote(request.getLoteId(), request.getTipoLabor());
        Maquinaria maquinaria = buscarYValidarMaquinaria(
                request.getMaquinariaId(),
                request.getFechaPlanificada(),
                labor.getId()
        );

        // 1. Liberar reservas activas actuales antes de recalcular disponibilidad
        liberarReservasActivas(labor);
        labor.getInsumos().clear();
        laborRepository.flush();

        // 2. Validar y reservar la nueva grilla de insumos
        Map<Long, Insumo> insumosBloqueados = bloquearYValidarStockDisponible(itemsSolicitados);

        labor.setLote(lote);
        labor.setMaquinaria(maquinaria);
        labor.setTipoLabor(request.getTipoLabor());
        labor.setFechaPlanificada(request.getFechaPlanificada());
        labor.setEstado(EstadoLabor.PLANIFICADA);
        limpiarSnapshotClimatico(labor);
        labor.setObservacionesClimaticas(textoOpcional(request.getObservaciones()));

        reservarInsumosEnLabor(labor, itemsSolicitados, insumosBloqueados);

        return LaborResponse.from(laborRepository.save(labor));
    }

    @Transactional
    public LaborResponse reprogramar(Long id, ReprogramarLaborRequest request) {
        LaborAgricola labor = buscarLaborParaActualizar(id);

        if (!labor.getEstado().admiteReprogramacionOCancelacion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede reprogramar una labor en estado " + labor.getEstado());
        }

        if (labor.getMaquinaria() != null) {
            validarSinConflictoHorarioMaquinaria(
                    labor.getMaquinaria(),
                    request.getFechaPlanificada(),
                    labor.getId()
            );
        }

        labor.setFechaPlanificada(request.getFechaPlanificada());
        labor.setEstado(EstadoLabor.PLANIFICADA);
        limpiarSnapshotClimatico(labor);

        String obs = textoOpcional(request.getObservaciones());
        if (obs != null) {
            labor.setObservacionesClimaticas(obs);
        }

        return LaborResponse.from(laborRepository.save(labor));
    }

    @Transactional
    public LaborResponse cancelar(Long id) {
        LaborAgricola labor = buscarLaborParaActualizar(id);

        if (!labor.getEstado().admiteReprogramacionOCancelacion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede cancelar una labor en estado " + labor.getEstado());
        }

        liberarReservasActivas(labor);
        labor.setEstado(EstadoLabor.CANCELADA);
        return LaborResponse.from(laborRepository.save(labor));
    }

    @Transactional
    public LaborResponse iniciar(Long id, String username) {
        LaborAgricola labor = buscarLaborParaActualizar(id);

        if (labor.getEstado() == EstadoLabor.BLOQUEADA_CLIMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede iniciar una labor en estado BLOQUEADA_CLIMA; debe reprogramarse o validarse climáticamente");
        }
        if (!labor.getEstado().admiteInicioEjecucion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede iniciar una labor en estado " + labor.getEstado());
        }
        // RN-01 / doc 06 §6.3.2: sólo LABRANZA pasa directo de PLANIFICADA a EN_EJECUCION;
        // el resto debe superar antes la validación climática (APROBADA_CLIMA, HU-10).
        if (labor.getEstado() == EstadoLabor.PLANIFICADA
                && labor.getTipoLabor().requiereValidacionClimatica()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La labor de " + labor.getTipoLabor()
                            + " requiere validación climática previa (estado APROBADA_CLIMA, RN-01) antes de iniciarse."
                            + " Sólo las labores de LABRANZA pueden iniciarse directamente desde PLANIFICADA");
        }

        Usuario usuario = buscarUsuario(username);
        Lote lote = buscarYValidarLote(labor.getLote().getId(), labor.getTipoLabor());
        LocalDateTime ahora = LocalDateTime.now();

        if (labor.getMaquinaria() != null) {
            Maquinaria maq = maquinariaRepository.findByIdForUpdate(labor.getMaquinaria().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Maquinaria no encontrada con id: " + labor.getMaquinaria().getId()));
            if (maq.getEstado() != EstadoMaquinaria.OPERATIVO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La maquinaria '" + maq.getNombre()
                                + "' no está disponible para iniciar la labor (estado actual: "
                                + maq.getEstado() + "; debe estar OPERATIVO)");
            }
            maq.setEstado(EstadoMaquinaria.EN_LABOR);
            maquinariaRepository.save(maq);
            labor.setMaquinaria(maq);
        }

        consumirReservasActivas(labor, usuario, lote, ahora);

        labor.setLote(lote);
        labor.setFechaEjecucion(ahora);
        labor.setEstado(EstadoLabor.EN_EJECUCION);

        return LaborResponse.from(laborRepository.save(labor));
    }

    @Transactional
    public LaborResponse completar(Long id, CompletarLaborRequest request) {
        LaborAgricola labor = buscarLaborParaActualizar(id);

        // doc 06 §6.3.2: COMPLETADA sólo se alcanza desde EN_EJECUCION (RN-08).
        if (!labor.getEstado().admiteCierre()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sólo se puede completar una labor EN_EJECUCION (estado actual: " + labor.getEstado()
                            + "). Iniciá la labor antes de registrar su cierre");
        }

        LocalDateTime ahora = LocalDateTime.now();
        BigDecimal horasReales = request != null ? request.getHorasMaquinaConsumidas() : null;

        if (labor.getMaquinaria() != null) {
            if (horasReales == null || horasReales.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe informar las horas reales de máquina empleadas (mayores a 0) para completar una labor con maquinaria asignada");
            }
            validarTopeHorasMaquina(horasReales, labor.getFechaEjecucion(), ahora);
        } else {
            if (horasReales != null && horasReales.compareTo(BigDecimal.ZERO) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se pueden registrar horas de máquina mayores a 0 en una labor manual sin maquinaria asignada");
            }
            horasReales = BigDecimal.ZERO;
        }

        Lote lote = loteRepository.findByIdForUpdate(labor.getLote().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + labor.getLote().getId()));

        BigDecimal costoMaquinaria = BigDecimal.ZERO;
        if (labor.getMaquinaria() != null) {
            Maquinaria maq = maquinariaRepository.findByIdForUpdate(labor.getMaquinaria().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Maquinaria no encontrada con id: " + labor.getMaquinaria().getId()));
            if (maq.getEstado() != EstadoMaquinaria.EN_LABOR) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La maquinaria '" + maq.getNombre()
                                + "' se encuentra en un estado inválido para cerrar la labor (estado actual: "
                                + maq.getEstado() + "; se esperaba EN_LABOR)");
            }
            maq.sumarHoras(horasReales);
            maq.setEstado(EstadoMaquinaria.OPERATIVO);
            maquinariaRepository.save(maq);
            labor.setMaquinaria(maq);
            costoMaquinaria = horasReales.multiply(maq.getCostoOperativoHora()).setScale(2, RoundingMode.HALF_UP);
        }

        // Los insumos se consumieron al iniciar (RN-07): su costo_subtotal ya es histórico (RN-05).
        BigDecimal totalInsumos = labor.getInsumos().stream()
                .filter(i -> i.getEstadoReserva() == EstadoReserva.CONSUMIDO)
                .map(LaborInsumo::getCostoSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        labor.setHorasMaquinaConsumidas(horasReales);
        labor.setCostoTotalCalculado(totalInsumos.add(costoMaquinaria).setScale(2, RoundingMode.HALF_UP));
        labor.setEstado(EstadoLabor.COMPLETADA);

        String obsCierre = request != null ? textoOpcional(request.getObservaciones()) : null;
        if (obsCierre != null) {
            String previas = labor.getObservacionesClimaticas();
            labor.setObservacionesClimaticas(
                    previas == null || previas.isBlank() ? obsCierre : previas + " | Cierre: " + obsCierre
            );
        }

        if (labor.getTipoLabor() == TipoLabor.SIEMBRA && lote.getEstado() == EstadoLote.EN_PREPARACION) {
            lote.setEstado(EstadoLote.SEMBRADO);
            loteRepository.save(lote);
        }
        labor.setLote(lote);

        return LaborResponse.from(laborRepository.save(labor));
    }

    /**
     * Una máquina no puede trabajar más de 24 hs por día calendario: el tope es
     * 24 hs por cada día transcurrido desde el inicio de la labor (incluido el día de inicio).
     */
    private void validarTopeHorasMaquina(BigDecimal horas, LocalDateTime fechaInicio, LocalDateTime ahora) {
        if (fechaInicio == null) {
            return;
        }
        long dias = ChronoUnit.DAYS.between(fechaInicio.toLocalDate(), ahora.toLocalDate()) + 1;
        BigDecimal tope = BigDecimal.valueOf(HORAS_POR_DIA * Math.max(1, dias));
        if (horas.compareTo(tope) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las horas de máquina informadas (" + horas + " hs) superan el máximo posible desde el inicio de la labor ("
                            + tope + " hs: 24 hs por día calendario desde el "
                            + fechaInicio.toLocalDate().format(FORMATO_FECHA) + ")");
        }
    }

    private Lote buscarYValidarLote(Long loteId, TipoLabor tipoLabor) {
        Lote lote = loteRepository.findByIdForUpdate(loteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + loteId));

        EstadoLote estadoLote = lote.getEstado();

        if ((estadoLote == EstadoLote.COSECHADO || estadoLote == EstadoLote.DESCANSO)
                && tipoLabor != TipoLabor.LABRANZA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede programar ni ejecutar una labor de " + tipoLabor
                            + " en el lote '" + lote.getNombre() + "' porque se encuentra en estado "
                            + estadoLote + " (sólo se admite LABRANZA)");
        }

        if (tipoLabor == TipoLabor.SIEMBRA && estadoLote != EstadoLote.EN_PREPARACION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sólo se puede programar o ejecutar una labor de SIEMBRA cuando el lote está EN_PREPARACION (estado actual de '"
                            + lote.getNombre() + "': " + estadoLote + ")");
        }

        if (tipoLabor == TipoLabor.LABRANZA
                && (estadoLote == EstadoLote.SEMBRADO
                || estadoLote == EstadoLote.EN_CRECIMIENTO
                || estadoLote == EstadoLote.LISTO_COSECHA)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede programar ni ejecutar una labor de LABRANZA en el lote '" + lote.getNombre()
                            + "' porque tiene un cultivo activo en estado " + estadoLote);
        }

        if (tipoLabor == TipoLabor.COSECHA
                && estadoLote != EstadoLote.LISTO_COSECHA
                && estadoLote != EstadoLote.EN_CRECIMIENTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sólo se puede programar o ejecutar una labor de COSECHA cuando el lote está LISTO_COSECHA o EN_CRECIMIENTO (estado actual de '"
                            + lote.getNombre() + "': " + estadoLote + ")");
        }

        return lote;
    }

    private Maquinaria buscarYValidarMaquinaria(Long maquinariaId, LocalDateTime fechaPlanificada, Long excluirLaborId) {
        if (maquinariaId == null) {
            return null;
        }
        Maquinaria maquinaria = maquinariaRepository.findByIdForUpdate(maquinariaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Maquinaria no encontrada con id: " + maquinariaId));

        if (maquinaria.getEstado() != EstadoMaquinaria.OPERATIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La maquinaria '" + maquinaria.getNombre()
                            + "' no está disponible para asignar a la labor (estado actual: "
                            + maquinaria.getEstado() + "; debe estar OPERATIVO)");
        }

        validarSinConflictoHorarioMaquinaria(maquinaria, fechaPlanificada, excluirLaborId);
        return maquinaria;
    }

    private void validarSinConflictoHorarioMaquinaria(
            Maquinaria maquinaria,
            LocalDateTime fechaPlanificada,
            Long excluirLaborId) {
        LocalDateTime desde = fechaPlanificada.minusMinutes(VENTANA_CONFLICTO_MINUTOS);
        LocalDateTime hasta = fechaPlanificada.plusMinutes(VENTANA_CONFLICTO_MINUTOS);

        List<LaborAgricola> conflictos = laborRepository.buscarConflictosHorariosMaquinaria(
                maquinaria.getId(),
                ESTADOS_PENDIENTES,
                desde,
                hasta,
                excluirLaborId
        );

        if (!conflictos.isEmpty()) {
            LaborAgricola conflicto = conflictos.get(0);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La maquinaria '" + maquinaria.getNombre()
                            + "' ya está asignada a la orden pendiente #" + conflicto.getId()
                            + " en el lote '" + conflicto.getLote().getNombre()
                            + "' para las " + conflicto.getFechaPlanificada()
                            + " (se requiere al menos 2 horas de separación entre órdenes)");
        }
    }

    private List<LaborInsumoRequest> validarItemsSinDuplicados(List<LaborInsumoRequest> items) {
        List<LaborInsumoRequest> itemsSolicitados = items != null ? items : List.of();
        Set<Long> idsVistos = new HashSet<>();
        for (LaborInsumoRequest item : itemsSolicitados) {
            if (!idsVistos.add(item.getInsumoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se puede repetir el mismo insumo en la misma orden de trabajo (insumo id: "
                                + item.getInsumoId() + ")");
            }
        }
        return itemsSolicitados;
    }

    private Map<Long, Insumo> bloquearYValidarStockDisponible(List<LaborInsumoRequest> itemsSolicitados) {
        Map<Long, Insumo> insumosBloqueados = new HashMap<>();
        List<LaborInsumoRequest> ordenadosPorId = itemsSolicitados.stream()
                .sorted(Comparator.comparing(LaborInsumoRequest::getInsumoId))
                .toList();

        for (LaborInsumoRequest item : ordenadosPorId) {
            Insumo insumo = insumoRepository.findByIdForUpdate(item.getInsumoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Insumo no encontrado con id: " + item.getInsumoId()));

            BigDecimal disponible = insumo.getStockDisponible();
            if (disponible.compareTo(item.getCantidadUtilizada()) < 0) {
                BigDecimal faltante = item.getCantidadUtilizada().subtract(disponible);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock disponible insuficiente para el insumo '" + insumo.getNombre()
                                + "': disponible " + disponible + " " + insumo.getUnidadMedida()
                                + ", requerido " + item.getCantidadUtilizada() + " " + insumo.getUnidadMedida()
                                + " (faltan " + faltante + " " + insumo.getUnidadMedida() + ")");
            }
            insumosBloqueados.put(insumo.getId(), insumo);
        }
        return insumosBloqueados;
    }

    private void reservarInsumosEnLabor(
            LaborAgricola labor,
            List<LaborInsumoRequest> itemsSolicitados,
            Map<Long, Insumo> insumosBloqueados) {
        for (LaborInsumoRequest item : itemsSolicitados) {
            Insumo insumo = insumosBloqueados.get(item.getInsumoId());
            insumo.reservar(item.getCantidadUtilizada());
            insumoRepository.save(insumo);

            LaborInsumo linea = new LaborInsumo();
            linea.setInsumo(insumo);
            linea.setCantidadUtilizada(item.getCantidadUtilizada());
            linea.setCostoSubtotal(BigDecimal.ZERO);
            linea.setEstadoReserva(EstadoReserva.RESERVADO);
            labor.agregarInsumo(linea);
        }
    }

    private void consumirReservasActivas(
            LaborAgricola labor,
            Usuario usuario,
            Lote lote,
            LocalDateTime fechaMovimiento) {
        List<LaborInsumo> reservasActivas = labor.getInsumos().stream()
                .filter(i -> i.getEstadoReserva() == EstadoReserva.RESERVADO)
                .sorted(Comparator.comparing(i -> i.getInsumo().getId()))
                .toList();

        for (LaborInsumo linea : reservasActivas) {
            Insumo insumo = insumoRepository.findByIdForUpdate(linea.getInsumo().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Insumo no encontrado con id: " + linea.getInsumo().getId()));

            BigDecimal cantidad = linea.getCantidadUtilizada();
            if (insumo.getStockActual().compareTo(cantidad) < 0) {
                BigDecimal faltante = cantidad.subtract(insumo.getStockActual());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock físico insuficiente para el insumo '" + insumo.getNombre()
                                + "': stock actual " + insumo.getStockActual() + " " + insumo.getUnidadMedida()
                                + ", requerido " + cantidad + " " + insumo.getUnidadMedida()
                                + " (faltan " + faltante + " " + insumo.getUnidadMedida() + ")");
            }

            insumo.consumirReserva(cantidad);
            insumoRepository.save(insumo);

            BigDecimal subtotal = cantidad.multiply(insumo.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP);
            linea.setCostoSubtotal(subtotal);
            linea.setEstadoReserva(EstadoReserva.CONSUMIDO);
            linea.setInsumo(insumo);

            MovimientoStock mov = new MovimientoStock();
            mov.setInsumo(insumo);
            mov.setFecha(fechaMovimiento);
            mov.setTipo(TipoMovimientoStock.EGRESO_LABOR);
            mov.setCantidad(cantidad.negate());
            mov.setUsuario(usuario);
            mov.setLaborId(labor.getId());
            mov.setObservacion("Consumo en labor #" + labor.getId()
                    + " (" + labor.getTipoLabor() + ") en " + lote.getNombre());
            movimientoStockRepository.save(mov);
        }
    }

    private void liberarReservasActivas(LaborAgricola labor) {
        List<LaborInsumo> reservasActivas = labor.getInsumos().stream()
                .filter(i -> i.getEstadoReserva() == EstadoReserva.RESERVADO)
                .sorted(Comparator.comparing(i -> i.getInsumo().getId()))
                .toList();

        for (LaborInsumo linea : reservasActivas) {
            Insumo insumo = insumoRepository.findByIdForUpdate(linea.getInsumo().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Insumo no encontrado con id: " + linea.getInsumo().getId()));
            insumo.liberarReserva(linea.getCantidadUtilizada());
            insumoRepository.save(insumo);
            linea.setEstadoReserva(EstadoReserva.LIBERADO);
        }
    }

    private void limpiarSnapshotClimatico(LaborAgricola labor) {
        labor.setTemperaturaRegistrada(null);
        labor.setVientoVelocidadKmh(null);
        labor.setProbabilidadLluviaPct(null);
        labor.setHumedadRegistradaPct(null);
    }

    private LaborAgricola buscarLabor(Long id) {
        return laborRepository.findConDetalleById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Labor agrícola no encontrada con id: " + id));
    }

    private LaborAgricola buscarLaborParaActualizar(Long id) {
        return laborRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Labor agrícola no encontrada con id: " + id));
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado: " + username));
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
