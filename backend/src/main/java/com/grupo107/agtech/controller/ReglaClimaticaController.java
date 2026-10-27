package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.EvaluacionClimaRequest;
import com.grupo107.agtech.controller.dto.EvaluacionClimaResponse;
import com.grupo107.agtech.controller.dto.ReglaClimaticaRequest;
import com.grupo107.agtech.controller.dto.ReglaClimaticaResponse;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.service.ReglaClimaticaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reglas-climaticas")
public class ReglaClimaticaController {

    private final ReglaClimaticaService reglaClimaticaService;

    public ReglaClimaticaController(ReglaClimaticaService reglaClimaticaService) {
        this.reglaClimaticaService = reglaClimaticaService;
    }

    @GetMapping
    public List<ReglaClimaticaResponse> listar(@RequestParam(required = false) TipoLabor tipoLabor) {
        return reglaClimaticaService.listar(tipoLabor);
    }

    @GetMapping("/{id}")
    public ReglaClimaticaResponse obtenerPorId(@PathVariable Long id) {
        return reglaClimaticaService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReglaClimaticaResponse> crear(@Valid @RequestBody ReglaClimaticaRequest request) {
        ReglaClimaticaResponse creada = reglaClimaticaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReglaClimaticaResponse actualizar(@PathVariable Long id,
                                             @Valid @RequestBody ReglaClimaticaRequest request) {
        return reglaClimaticaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        reglaClimaticaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/evaluar")
    public EvaluacionClimaResponse evaluar(@PathVariable Long id,
                                           @Valid @RequestBody EvaluacionClimaRequest request) {
        return reglaClimaticaService.evaluar(id, request);
    }
}
