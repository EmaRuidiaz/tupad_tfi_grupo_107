package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.MantenimientoRequest;
import com.grupo107.agtech.controller.dto.MantenimientoResponse;
import com.grupo107.agtech.model.EstadoMaquinaria;
import com.grupo107.agtech.model.Maquinaria;
import com.grupo107.agtech.model.RegistroMantenimiento;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.MaquinariaRepository;
import com.grupo107.agtech.repository.RegistroMantenimientoRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MantenimientoService {

    private final RegistroMantenimientoRepository mantenimientoRepository;
    private final MaquinariaRepository maquinariaRepository;
    private final UsuarioRepository usuarioRepository;

    public MantenimientoService(RegistroMantenimientoRepository mantenimientoRepository,
                                MaquinariaRepository maquinariaRepository,
                                UsuarioRepository usuarioRepository) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.maquinariaRepository = maquinariaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<MantenimientoResponse> listarPorMaquinaria(Long maquinariaId) {
        if (!maquinariaRepository.existsById(maquinariaId)) {
            throw maquinariaNoEncontrada(maquinariaId);
        }
        return mantenimientoRepository.findByMaquinariaIdOrderByFechaServiceDescIdDesc(maquinariaId).stream()
                .map(MantenimientoResponse::from)
                .toList();
    }

    // CU-06: registra el service, copia el próximo umbral a la máquina y la deja OPERATIVO.
    @Transactional
    public MantenimientoResponse registrar(Long maquinariaId, MantenimientoRequest request, String username) {
        Maquinaria maquinaria = maquinariaRepository.findByIdForUpdate(maquinariaId)
                .orElseThrow(() -> maquinariaNoEncontrada(maquinariaId));

        if (maquinaria.getEstado() == EstadoMaquinaria.EN_LABOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede registrar un service mientras la maquinaria está asignada a una labor en ejecución");
        }
        if (maquinaria.getEstado() == EstadoMaquinaria.FUERA_DE_SERVICIO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La maquinaria está FUERA_DE_SERVICIO: un administrador debe reingresarla a taller (EN_MANTENIMIENTO) antes de registrar el service");
        }

        BigDecimal lectura = request.getHorasMaquinaEnService();
        BigDecimal proximo = request.getProximoServiceHoras();

        mantenimientoRepository.findFirstByMaquinariaIdOrderByHorasMaquinaEnServiceDescIdDesc(maquinariaId)
                .ifPresent(ultimo -> {
                    if (lectura.compareTo(ultimo.getHorasMaquinaEnService()) < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "La lectura del horómetro no puede ser menor a la del último service registrado ("
                                        + ultimo.getHorasMaquinaEnService() + " hs)");
                    }
                    if (request.getFechaService().isBefore(ultimo.getFechaService())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "La fecha del service no puede ser anterior a la del último service registrado ("
                                        + ultimo.getFechaService() + ")");
                    }
                });

        if (proximo.compareTo(lectura) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las horas del próximo service deben ser mayores a la lectura del horómetro en el service");
        }

        // La lectura del service es la medición más reciente del horómetro.
        if (lectura.compareTo(maquinaria.getHorasUsoActuales()) > 0) {
            maquinaria.setHorasUsoActuales(lectura);
        }
        if (proximo.compareTo(maquinaria.getHorasUsoActuales()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las horas del próximo service deben ser mayores al horómetro actual de la máquina ("
                            + maquinaria.getHorasUsoActuales() + " hs)");
        }

        RegistroMantenimiento registro = new RegistroMantenimiento();
        registro.setMaquinaria(maquinaria);
        registro.setUsuario(buscarUsuario(username));
        registro.setFechaService(request.getFechaService());
        registro.setHorasMaquinaEnService(lectura);
        registro.setTipoMantenimiento(request.getTipoMantenimiento());
        registro.setCosto(request.getCosto());
        registro.setProximoServiceHoras(proximo);
        registro.setDescripcion(request.getDescripcion() != null && !request.getDescripcion().isBlank()
                ? request.getDescripcion().trim()
                : null);

        maquinaria.setHorasParaProximoService(proximo);
        maquinaria.setEstado(EstadoMaquinaria.OPERATIVO);
        maquinariaRepository.save(maquinaria);

        return MantenimientoResponse.from(mantenimientoRepository.save(registro));
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado: " + username));
    }

    private ResponseStatusException maquinariaNoEncontrada(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Maquinaria no encontrada con id: " + id);
    }
}
