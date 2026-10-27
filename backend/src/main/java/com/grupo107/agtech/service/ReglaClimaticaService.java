package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.ReglaClimaticaRequest;
import com.grupo107.agtech.controller.dto.ReglaClimaticaResponse;
import com.grupo107.agtech.model.ReglaClimatica;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.repository.ReglaClimaticaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReglaClimaticaService {

    private final ReglaClimaticaRepository reglaClimaticaRepository;

    public ReglaClimaticaService(ReglaClimaticaRepository reglaClimaticaRepository) {
        this.reglaClimaticaRepository = reglaClimaticaRepository;
    }

    @Transactional(readOnly = true)
    public List<ReglaClimaticaResponse> listar(TipoLabor tipoLabor) {
        if (tipoLabor != null) {
            return reglaClimaticaRepository.findByTipoLabor(tipoLabor)
                    .map(r -> List.of(ReglaClimaticaResponse.from(r)))
                    .orElseGet(List::of);
        }
        return reglaClimaticaRepository.findAllByOrderByIdAsc().stream()
                .map(ReglaClimaticaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReglaClimaticaResponse obtenerPorId(Long id) {
        return ReglaClimaticaResponse.from(buscarRegla(id));
    }

    @Transactional
    public ReglaClimaticaResponse crear(ReglaClimaticaRequest request) {
        validarReglasNegocio(request);

        if (reglaClimaticaRepository.existsByTipoLabor(request.getTipoLabor())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ya existe una regla climática configurada para el tipo de labor: " + request.getTipoLabor());
        }

        ReglaClimatica regla = new ReglaClimatica();
        aplicarDatos(regla, request);
        return ReglaClimaticaResponse.from(reglaClimaticaRepository.save(regla));
    }

    @Transactional
    public ReglaClimaticaResponse actualizar(Long id, ReglaClimaticaRequest request) {
        ReglaClimatica regla = buscarReglaParaActualizar(id);
        validarReglasNegocio(request);

        if (reglaClimaticaRepository.existsByTipoLaborAndIdNot(request.getTipoLabor(), id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ya existe otra regla climática configurada para el tipo de labor: " + request.getTipoLabor());
        }

        aplicarDatos(regla, request);
        return ReglaClimaticaResponse.from(reglaClimaticaRepository.save(regla));
    }

    @Transactional
    public void eliminar(Long id) {
        ReglaClimatica regla = buscarReglaParaActualizar(id);
        reglaClimaticaRepository.delete(regla);
        reglaClimaticaRepository.flush();
    }

    private void validarReglasNegocio(ReglaClimaticaRequest request) {
        if (!request.getTipoLabor().requiereValidacionClimatica()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las labores de tipo LABRANZA no llevan regla climática asociada (RN-01)");
        }
        if (request.getVientoMinKmh().compareTo(request.getVientoMaxKmh()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La velocidad mínima de viento (" + request.getVientoMinKmh()
                            + " km/h) no puede superar a la velocidad máxima (" + request.getVientoMaxKmh() + " km/h)");
        }
        if (request.getTempMinC().compareTo(request.getTempMaxC()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La temperatura mínima (" + request.getTempMinC()
                            + " °C) debe ser menor a la temperatura máxima (" + request.getTempMaxC() + " °C)");
        }
    }

    private void aplicarDatos(ReglaClimatica regla, ReglaClimaticaRequest request) {
        regla.setTipoLabor(request.getTipoLabor());
        regla.setVientoMinKmh(request.getVientoMinKmh());
        regla.setVientoMaxKmh(request.getVientoMaxKmh());
        regla.setTempMinC(request.getTempMinC());
        regla.setTempMaxC(request.getTempMaxC());
        regla.setProbLluviaMaxPct(request.getProbLluviaMaxPct());
        regla.setHumedadMinPct(request.getHumedadMinPct());
        regla.setMensajeAlerta(request.getMensajeAlerta().trim());
    }

    private ReglaClimatica buscarRegla(Long id) {
        return reglaClimaticaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Regla climática no encontrada con id: " + id));
    }

    private ReglaClimatica buscarReglaParaActualizar(Long id) {
        return reglaClimaticaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Regla climática no encontrada con id: " + id));
    }
}
