package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.ClimaLoteResponse;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.service.clima.ConsultaClima;
import com.grupo107.agtech.service.clima.ProveedorClima;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClimaService {

    private final LoteRepository loteRepository;
    private final ProveedorClima proveedorClima;

    public ClimaService(LoteRepository loteRepository, ProveedorClima proveedorClima) {
        this.loteRepository = loteRepository;
        this.proveedorClima = proveedorClima;
    }

    // Sin @Transactional: no se retiene una conexión a la base mientras se espera a la API externa.
    public ClimaLoteResponse obtenerClimaDeLote(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lote no encontrado con id: " + loteId));

        ConsultaClima consulta = proveedorClima.obtenerClimaActual(lote.getLatitud(), lote.getLongitud());
        return ClimaLoteResponse.from(lote, consulta);
    }
}
