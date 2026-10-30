package com.grupo107.agtech.service;

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
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.InsumoRepository;
import com.grupo107.agtech.repository.LaborAgricolaRepository;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.repository.MaquinariaRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LaborService {

    private final LaborAgricolaRepository laborRepository;
    private final LoteRepository loteRepository;
    private final MaquinariaRepository maquinariaRepository;
    private final InsumoRepository insumoRepository;
    private final UsuarioRepository usuarioRepository;

    public LaborService(LaborAgricolaRepository laborRepository,
                        LoteRepository loteRepository,
                        MaquinariaRepository maquinariaRepository,
                        InsumoRepository insumoRepository,
                        UsuarioRepository usuarioRepository) {
        this.laborRepository = laborRepository;
        this.loteRepository = loteRepository;
        this.maquinariaRepository = maquinariaRepository;
        this.insumoRepository = insumoRepository;
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
        Lote lote = loteRepository.findByIdForUpdate(request.getLoteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + request.getLoteId()));

        if ((lote.getEstado() == EstadoLote.COSECHADO || lote.getEstado() == EstadoLote.DESCANSO)
                && request.getTipoLabor() != TipoLabor.LABRANZA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede programar una labor de " + request.getTipoLabor()
                            + " en el lote '" + lote.getNombre() + "' porque se encuentra en estado "
                            + lote.getEstado() + " (sólo se admite LABRANZA)");
        }

        Maquinaria maquinaria = null;
        if (request.getMaquinariaId() != null) {
            maquinaria = maquinariaRepository.findByIdForUpdate(request.getMaquinariaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Maquinaria no encontrada con id: " + request.getMaquinariaId()));
            if (maquinaria.getEstado() != EstadoMaquinaria.OPERATIVO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La maquinaria '" + maquinaria.getNombre()
                                + "' no está disponible para asignar a la labor (estado actual: "
                                + maquinaria.getEstado() + "; debe estar OPERATIVO)");
            }
        }

        List<LaborInsumoRequest> itemsSolicitados = request.getInsumos() != null
                ? request.getInsumos()
                : List.of();

        Set<Long> idsVistos = new HashSet<>();
        for (LaborInsumoRequest item : itemsSolicitados) {
            if (!idsVistos.add(item.getInsumoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se puede repetir el mismo insumo en la misma orden de trabajo (insumo id: "
                                + item.getInsumoId() + ")");
            }
        }

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

        return LaborResponse.from(laborRepository.save(labor));
    }

    @Transactional
    public LaborResponse reprogramar(Long id, ReprogramarLaborRequest request) {
        LaborAgricola labor = buscarLaborParaActualizar(id);

        if (!labor.getEstado().admiteReprogramacionOCancelacion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede reprogramar una labor en estado " + labor.getEstado());
        }

        labor.setFechaPlanificada(request.getFechaPlanificada());
        labor.setEstado(EstadoLabor.PLANIFICADA);
        labor.setTemperaturaRegistrada(null);
        labor.setVientoVelocidadKmh(null);
        labor.setProbabilidadLluviaPct(null);
        labor.setHumedadRegistradaPct(null);

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

        labor.setEstado(EstadoLabor.CANCELADA);
        return LaborResponse.from(laborRepository.save(labor));
    }

    private LaborAgricola buscarLabor(Long id) {
        return laborRepository.findById(id)
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
