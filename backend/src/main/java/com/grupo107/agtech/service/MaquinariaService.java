package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.MaquinariaRequest;
import com.grupo107.agtech.controller.dto.MaquinariaResponse;
import com.grupo107.agtech.model.EstadoMaquinaria;
import com.grupo107.agtech.model.Maquinaria;
import com.grupo107.agtech.repository.MaquinariaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MaquinariaService {

    private final MaquinariaRepository maquinariaRepository;
    private final JdbcTemplate jdbcTemplate;

    public MaquinariaService(MaquinariaRepository maquinariaRepository, JdbcTemplate jdbcTemplate) {
        this.maquinariaRepository = maquinariaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<MaquinariaResponse> listar(EstadoMaquinaria estado, Boolean mantenimientoVencido) {
        return maquinariaRepository.findAllByOrderByIdAsc().stream()
                .filter(m -> estado == null || m.getEstado() == estado)
                .filter(m -> mantenimientoVencido == null || m.isMantenimientoVencido() == mantenimientoVencido)
                .map(MaquinariaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaquinariaResponse obtenerPorId(Long id) {
        return MaquinariaResponse.from(buscarMaquinaria(id));
    }

    @Transactional
    public MaquinariaResponse crear(MaquinariaRequest request) {
        String matricula = request.getMatriculaOSerie().trim();
        if (maquinariaRepository.existsByMatriculaOSerieIgnoreCase(matricula)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ya existe una maquinaria registrada con la matrícula o serie: " + matricula);
        }

        BigDecimal horasIniciales = request.getHorasUsoActuales() != null
                ? request.getHorasUsoActuales()
                : BigDecimal.ZERO;

        if (request.getHorasParaProximoService().compareTo(horasIniciales) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las horas para el próximo service deben ser mayores a las horas de uso iniciales");
        }

        Maquinaria m = new Maquinaria();
        m.setNombre(request.getNombre().trim());
        m.setTipo(request.getTipo());
        m.setMatriculaOSerie(matricula);
        m.setHorasUsoActuales(horasIniciales);
        m.setHorasParaProximoService(request.getHorasParaProximoService());
        m.setConsumoLitrosHora(request.getConsumoLitrosHora());
        m.setCostoOperativoHora(request.getCostoOperativoHora());
        m.setEstado(EstadoMaquinaria.OPERATIVO);

        return MaquinariaResponse.from(maquinariaRepository.save(m));
    }

    @Transactional
    public MaquinariaResponse actualizar(Long id, MaquinariaRequest request) {
        Maquinaria m = buscarMaquinariaParaActualizar(id);

        if (m.getEstado() == EstadoMaquinaria.EN_LABOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede modificar una maquinaria mientras está asignada a una labor en ejecución");
        }

        String matricula = request.getMatriculaOSerie().trim();

        if (maquinariaRepository.existsByMatriculaOSerieIgnoreCaseAndIdNot(matricula, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ya existe otra maquinaria registrada con la matrícula o serie: " + matricula);
        }

        m.setNombre(request.getNombre().trim());
        m.setTipo(request.getTipo());
        m.setMatriculaOSerie(matricula);
        if (request.getHorasUsoActuales() != null) {
            if (request.getHorasUsoActuales().compareTo(m.getHorasUsoActuales()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horómetro actual no puede reducirse por debajo del valor registrado (" + m.getHorasUsoActuales() + " hs)");
            }
            BigDecimal maxHorasService = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(horas_maquina_en_service), 0) FROM registro_mantenimiento WHERE maquinaria_id = ?",
                    BigDecimal.class, id);
            if (maxHorasService != null && request.getHorasUsoActuales().compareTo(maxHorasService) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horómetro no puede ser menor al último service registrado (" + maxHorasService + " hs)");
            }
            m.setHorasUsoActuales(request.getHorasUsoActuales());
        }
        m.setHorasParaProximoService(request.getHorasParaProximoService());
        m.setConsumoLitrosHora(request.getConsumoLitrosHora());
        m.setCostoOperativoHora(request.getCostoOperativoHora());

        return MaquinariaResponse.from(maquinariaRepository.save(m));
    }

    @Transactional
    public MaquinariaResponse cambiarEstado(Long id, EstadoMaquinaria nuevoEstado, boolean esAdmin) {
        Maquinaria m = buscarMaquinariaParaActualizar(id);
        EstadoMaquinaria actual = m.getEstado();

        if (actual == nuevoEstado) {
            return MaquinariaResponse.from(m);
        }

        validarTransicionManual(m, actual, nuevoEstado, esAdmin);
        m.setEstado(nuevoEstado);

        return MaquinariaResponse.from(maquinariaRepository.save(m));
    }

    @Transactional
    public void eliminar(Long id) {
        Maquinaria m = buscarMaquinariaParaActualizar(id);

        if (m.getEstado() == EstadoMaquinaria.EN_LABOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar una maquinaria que está asignada a una labor en ejecución");
        }

        Integer mantenimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registro_mantenimiento WHERE maquinaria_id = ?", Integer.class, id);
        Integer labores = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM labor_agricola WHERE maquinaria_id = ?", Integer.class, id);

        if ((mantenimientos != null && mantenimientos > 0) || (labores != null && labores > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la maquinaria porque registra labores o mantenimientos asociados. Pasala a FUERA_DE_SERVICIO para darla de baja lógica");
        }

        try {
            maquinariaRepository.delete(m);
            maquinariaRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar la maquinaria porque tiene registros asociados");
        }
    }

    private void validarTransicionManual(Maquinaria m, EstadoMaquinaria origen, EstadoMaquinaria destino, boolean esAdmin) {
        if (destino == EstadoMaquinaria.EN_LABOR || origen == EstadoMaquinaria.EN_LABOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El estado EN_LABOR sólo es asignado automáticamente al iniciar o completar una labor");
        }

        if (origen == EstadoMaquinaria.OPERATIVO && destino == EstadoMaquinaria.EN_MANTENIMIENTO) {
            return;
        }

        if ((origen == EstadoMaquinaria.OPERATIVO || origen == EstadoMaquinaria.EN_MANTENIMIENTO)
                && destino == EstadoMaquinaria.FUERA_DE_SERVICIO) {
            if (!esAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sólo un administrador puede pasar una maquinaria a FUERA_DE_SERVICIO");
            }
            return;
        }

        if (origen == EstadoMaquinaria.FUERA_DE_SERVICIO && destino == EstadoMaquinaria.EN_MANTENIMIENTO) {
            if (!esAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sólo un administrador puede reingresar a taller una maquinaria FUERA_DE_SERVICIO");
            }
            return;
        }

        if (origen == EstadoMaquinaria.EN_MANTENIMIENTO && destino == EstadoMaquinaria.OPERATIVO) {
            if (!esAdmin) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Para volver a OPERATIVO desde EN_MANTENIMIENTO debe registrarse el service correspondiente");
            }
            if (m.isMantenimientoVencido()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se puede volver a OPERATIVO una maquinaria con el service vencido; actualice el próximo umbral de service o registre el mantenimiento");
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Transición de estado no permitida: " + origen + " -> " + destino);
    }

    private Maquinaria buscarMaquinaria(Long id) {
        return maquinariaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Maquinaria no encontrada con id: " + id));
    }

    private Maquinaria buscarMaquinariaParaActualizar(Long id) {
        return maquinariaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Maquinaria no encontrada con id: " + id));
    }
}
