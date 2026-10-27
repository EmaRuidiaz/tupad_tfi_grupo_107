package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.MantenimientoRequest;
import com.grupo107.agtech.controller.dto.MantenimientoResponse;
import com.grupo107.agtech.service.MantenimientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maquinarias/{maquinariaId}/mantenimientos")
public class MantenimientoController {

    private final MantenimientoService mantenimientoService;

    public MantenimientoController(MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    @GetMapping
    public List<MantenimientoResponse> listar(@PathVariable Long maquinariaId) {
        return mantenimientoService.listarPorMaquinaria(maquinariaId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO', 'OPERARIO')")
    public ResponseEntity<MantenimientoResponse> registrar(@PathVariable Long maquinariaId,
                                                           @Valid @RequestBody MantenimientoRequest request,
                                                           Authentication authentication) {
        MantenimientoResponse creado = mantenimientoService.registrar(maquinariaId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}
