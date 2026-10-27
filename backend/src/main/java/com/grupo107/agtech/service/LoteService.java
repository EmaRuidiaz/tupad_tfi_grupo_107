package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.CambioEstadoLoteRequest;
import com.grupo107.agtech.controller.dto.LoteRequest;
import com.grupo107.agtech.controller.dto.LoteResponse;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LoteService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LoteRepository loteRepository;
    private final UsuarioRepository usuarioRepository;

    public LoteService(LoteRepository loteRepository, UsuarioRepository usuarioRepository) {
        this.loteRepository = loteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<LoteResponse> listarTodos(EstadoLote estado) {
        List<Lote> lotes = estado != null
                ? loteRepository.findByEstadoOrderByIdAsc(estado)
                : loteRepository.findAllByOrderByIdAsc();
        return lotes.stream().map(LoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LoteResponse obtenerPorId(Long id) {
        return LoteResponse.from(buscarLote(id));
    }

    @Transactional
    public LoteResponse crear(LoteRequest request, String username) {
        String nombre = request.getNombre().trim();
        if (loteRepository.existsByNombreIgnoreCase(nombre)) {
            throw nombreDuplicado(nombre);
        }

        Lote lote = new Lote();
        aplicarDatos(lote, request);
        lote.setEstado(EstadoLote.EN_PREPARACION);
        lote.setUsuario(buscarUsuario(username));

        return LoteResponse.from(loteRepository.save(lote));
    }

    @Transactional
    public LoteResponse actualizar(Long id, LoteRequest request) {
        Lote lote = buscarLoteParaActualizar(id);

        String nombre = request.getNombre().trim();
        if (loteRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw nombreDuplicado(nombre);
        }

        aplicarDatos(lote, request);
        return LoteResponse.from(loteRepository.save(lote));
    }

    @Transactional
    public void eliminar(Long id) {
        Lote lote = buscarLoteParaActualizar(id);
        if (loteRepository.contarLabores(id) > 0 || loteRepository.contarCosechas(id) > 0) {
            throw sinPoderEliminar();
        }
        try {
            loteRepository.delete(lote);
            loteRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw sinPoderEliminar();
        }
    }

    @Transactional
    public LoteResponse cambiarEstado(Long id, CambioEstadoLoteRequest request) {
        Lote lote = buscarLoteParaActualizar(id);
        EstadoLote actual = lote.getEstado();
        EstadoLote destino = request.getEstado();
        String observacion = textoOpcional(request.getObservacion());

        if (!actual.transicionesManuales().contains(destino)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transición de estado no permitida: " + actual + " -> " + destino);
        }
        if (actual.esPerdidaTotal(destino) && observacion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Volver a EN_PREPARACION desde " + actual
                            + " es una pérdida total del cultivo y requiere una observación");
        }

        lote.setEstado(destino);
        if (observacion != null) {
            agregarObservacion(lote, LocalDate.now().format(FORMATO_FECHA) + " - "
                    + actual + " -> " + destino + ": " + observacion);
        }
        return LoteResponse.from(loteRepository.save(lote));
    }

    private void aplicarDatos(Lote lote, LoteRequest request) {
        String poligono = textoOpcional(request.getPoligonoWkt());
        if (poligono != null) {
            try {
                PoligonoWktValidator.validar(poligono);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El polígono WKT es inválido: " + ex.getMessage());
            }
        }

        lote.setNombre(request.getNombre().trim());
        lote.setSuperficieHa(request.getSuperficieHa());
        lote.setLatitud(request.getLatitud());
        lote.setLongitud(request.getLongitud());
        lote.setPoligonoWkt(poligono);
        lote.setTipoSuelo(textoOpcional(request.getTipoSuelo()));
        lote.setObservaciones(textoOpcional(request.getObservaciones()));
    }

    private void agregarObservacion(Lote lote, String linea) {
        String previas = lote.getObservaciones();
        lote.setObservaciones(previas == null ? linea : previas + "\n" + linea);
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private ResponseStatusException nombreDuplicado(String nombre) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ya existe un lote con el nombre '" + nombre + "'");
    }

    private ResponseStatusException sinPoderEliminar() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede eliminar el lote porque tiene labores o cosechas asociadas");
    }

    private Lote buscarLote(Long id) {
        return loteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + id));
    }

    private Lote buscarLoteParaActualizar(Long id) {
        return loteRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + id));
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado: " + username));
    }
}
