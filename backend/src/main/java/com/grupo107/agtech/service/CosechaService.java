package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.CosechaRequest;
import com.grupo107.agtech.controller.dto.CosechaResponse;
import com.grupo107.agtech.controller.dto.CultivoResponse;
import com.grupo107.agtech.model.Cultivo;
import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.RegistroCosecha;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.CultivoRepository;
import com.grupo107.agtech.repository.LaborAgricolaRepository;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.repository.RegistroCosechaRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * HU-12 / CU-05: registro de la cosecha consolidada que cierra el ciclo productivo de un lote.
 * Las cosechas no se editan ni se eliminan: son el cierre inmutable del ciclo (RN-04, RN-12).
 */
@Service
public class CosechaService {

    private final RegistroCosechaRepository cosechaRepository;
    private final LoteRepository loteRepository;
    private final CultivoRepository cultivoRepository;
    private final LaborAgricolaRepository laborRepository;
    private final UsuarioRepository usuarioRepository;

    public CosechaService(RegistroCosechaRepository cosechaRepository,
                          LoteRepository loteRepository,
                          CultivoRepository cultivoRepository,
                          LaborAgricolaRepository laborRepository,
                          UsuarioRepository usuarioRepository) {
        this.cosechaRepository = cosechaRepository;
        this.loteRepository = loteRepository;
        this.cultivoRepository = cultivoRepository;
        this.laborRepository = laborRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<CultivoResponse> listarCultivos() {
        return cultivoRepository.findAllByOrderByNombreAsc().stream()
                .map(CultivoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CosechaResponse> listar(Long loteId) {
        List<RegistroCosecha> cosechas;
        if (loteId != null) {
            if (!loteRepository.existsById(loteId)) {
                throw loteNoEncontrado(loteId);
            }
            cosechas = cosechaRepository.findByLoteIdOrderByFechaCosechaDescIdDesc(loteId);
        } else {
            cosechas = cosechaRepository.findAllByOrderByFechaCosechaDescIdDesc();
        }
        return cosechas.stream().map(CosechaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CosechaResponse obtenerPorId(Long id) {
        return cosechaRepository.findConDetalleById(id)
                .map(CosechaResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cosecha no encontrada con id: " + id));
    }

    /**
     * CU-05: valida RN-02 y RN-12, calcula rinde, ingreso bruto y margen neto, y pasa el lote a COSECHADO.
     * Todo ocurre en una sola transacción: si algo falla, ni la cosecha ni el cambio de estado se guardan.
     */
    @Transactional
    public CosechaResponse registrar(CosechaRequest request, String username) {
        // Bloqueo pesimista: dos registros simultáneos sobre el mismo lote no pueden cerrar dos veces el ciclo.
        Lote lote = loteRepository.findByIdForUpdate(request.getLoteId())
                .orElseThrow(() -> loteNoEncontrado(request.getLoteId()));
        Cultivo cultivo = cultivoRepository.findById(request.getCultivoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cultivo no encontrado con id: " + request.getCultivoId()));

        // RN-02: sólo se cosecha un lote en madurez comercial.
        if (lote.getEstado() != EstadoLote.LISTO_COSECHA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sólo se puede registrar la cosecha de un lote en estado LISTO_COSECHA (estado actual de '"
                            + lote.getNombre() + "': " + lote.getEstado() + ")");
        }

        // RN-12: la fecha de cierre debe ser posterior al cierre del ciclo anterior.
        LocalDate fechaCosecha = request.getFechaCosecha();
        LocalDate cierreAnterior = cosechaRepository.findFirstByLoteIdOrderByFechaCosechaDescIdDesc(lote.getId())
                .map(RegistroCosecha::getFechaCosecha)
                .orElse(null);
        if (cierreAnterior != null && !fechaCosecha.isAfter(cierreAnterior)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de cosecha debe ser posterior a la última cosecha registrada en el lote ("
                            + cierreAnterior + ")");
        }

        LaborAgricola labor = request.getLaborId() != null
                ? validarLaborDeCosecha(request.getLaborId(), lote, cierreAnterior, fechaCosecha)
                : null;

        BigDecimal superficie = lote.getSuperficieHa();
        if (superficie == null || superficie.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El lote '" + lote.getNombre() + "' no tiene una superficie válida para calcular el rinde");
        }
        BigDecimal toneladas = request.getToneladasTotales().setScale(2, RoundingMode.HALF_UP);
        BigDecimal precio = request.getPrecioVentaPorTon().setScale(2, RoundingMode.HALF_UP);

        // Campos calculados del diccionario de datos (doc 03): se fijan al registrar y no se recalculan (RN-05).
        BigDecimal rinde = toneladas.divide(superficie, 2, RoundingMode.HALF_UP);
        BigDecimal ingresoBruto = toneladas.multiply(precio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costosCiclo = costosDelCiclo(lote.getId(), cierreAnterior, fechaCosecha);
        BigDecimal margenNeto = ingresoBruto.subtract(costosCiclo).setScale(2, RoundingMode.HALF_UP);

        verificarCapacidad(rinde, 6, "El rinde calculado");
        verificarCapacidad(ingresoBruto, 12, "El ingreso bruto calculado");
        verificarCapacidad(margenNeto, 12, "El margen neto calculado");

        RegistroCosecha cosecha = new RegistroCosecha();
        cosecha.setLote(lote);
        cosecha.setCultivo(cultivo);
        cosecha.setLabor(labor);
        cosecha.setUsuario(buscarUsuario(username)); // RN-04
        cosecha.setFechaCosecha(fechaCosecha);
        cosecha.setToneladasTotales(toneladas);
        cosecha.setRindeTonPorHa(rinde);
        cosecha.setPrecioVentaPorTon(precio);
        cosecha.setIngresoBrutoTotal(ingresoBruto);
        cosecha.setMargenNetoCalculado(margenNeto);

        // RN-02 + transición automática LISTO_COSECHA -> COSECHADO (doc 06, sección 6.3.1).
        lote.setEstado(EstadoLote.COSECHADO);
        loteRepository.save(lote);

        return CosechaResponse.from(cosechaRepository.save(cosecha));
    }

    /** La orden de trilla vinculada debe ser una labor de COSECHA completada, del mismo lote y del ciclo actual. */
    private LaborAgricola validarLaborDeCosecha(Long laborId, Lote lote, LocalDate cierreAnterior, LocalDate fechaCosecha) {
        LaborAgricola labor = laborRepository.findById(laborId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Labor no encontrada con id: " + laborId));

        if (!Objects.equals(labor.getLote().getId(), lote.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La labor " + laborId + " no pertenece al lote '" + lote.getNombre() + "'");
        }
        if (labor.getTipoLabor() != TipoLabor.COSECHA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La labor vinculada debe ser de tipo COSECHA (la labor " + laborId + " es de "
                            + labor.getTipoLabor() + ")");
        }
        if (labor.getEstado() != EstadoLabor.COMPLETADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La labor de cosecha " + laborId + " debe estar COMPLETADA para registrar la cosecha (estado actual: "
                            + labor.getEstado() + ")");
        }
        if (cosechaRepository.existsByLaborId(laborId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La labor " + laborId + " ya está vinculada a otra cosecha");
        }

        LocalDate fechaLabor = fechaEfectiva(labor);
        if (fechaLabor != null && cierreAnterior != null && !fechaLabor.isAfter(cierreAnterior)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La labor " + laborId + " pertenece a un ciclo productivo anterior (cerrado el " + cierreAnterior + ")");
        }
        if (fechaLabor != null && fechaLabor.isAfter(fechaCosecha)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de cosecha no puede ser anterior a la ejecución de la labor de cosecha (" + fechaLabor + ")");
        }
        return labor;
    }

    /**
     * RN-12: suma el costo de las labores COMPLETADA del lote ejecutadas dentro del ciclo que se cierra,
     * es decir, con {@code cierreAnterior < fecha <= fechaCosecha}. Usa el mismo criterio de fecha que el
     * Tablero Financiero (HU-13): fecha de ejecución o, si falta, la planificada.
     */
    private BigDecimal costosDelCiclo(Long loteId, LocalDate cierreAnterior, LocalDate fechaCosecha) {
        return laborRepository.findByLoteIdAndEstado(loteId, EstadoLabor.COMPLETADA).stream()
                .filter(l -> {
                    LocalDate fecha = fechaEfectiva(l);
                    if (fecha == null) {
                        return true;
                    }
                    boolean despuesDelCierreAnterior = cierreAnterior == null || fecha.isAfter(cierreAnterior);
                    return despuesDelCierreAnterior && !fecha.isAfter(fechaCosecha);
                })
                .map(l -> l.getCostoTotalCalculado() != null ? l.getCostoTotalCalculado() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate fechaEfectiva(LaborAgricola labor) {
        LocalDateTime fecha = labor.getFechaEjecucion() != null ? labor.getFechaEjecucion() : labor.getFechaPlanificada();
        return fecha != null ? fecha.toLocalDate() : null;
    }

    /** Evita un error de base por desbordar DECIMAL(p, 2): responde 400 con un mensaje claro. */
    private void verificarCapacidad(BigDecimal valor, int enterosMaximos, String campo) {
        int enteros = valor.abs().setScale(0, RoundingMode.DOWN).toPlainString().length();
        if (valor.abs().compareTo(BigDecimal.ONE) >= 0 && enteros > enterosMaximos) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    campo + " (" + valor.toPlainString() + ") excede la capacidad admitida (" + enterosMaximos
                            + " dígitos enteros)");
        }
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado: " + username));
    }

    private ResponseStatusException loteNoEncontrado(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lote no encontrado con id: " + id);
    }
}
