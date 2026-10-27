package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.CambioEstadoMaquinariaRequest;
import com.grupo107.agtech.controller.dto.MaquinariaRequest;
import com.grupo107.agtech.controller.dto.MaquinariaResponse;
import com.grupo107.agtech.model.EstadoMaquinaria;
import com.grupo107.agtech.service.MaquinariaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maquinarias")
public class MaquinariaController {

    private final MaquinariaService maquinariaService;

    public MaquinariaController(MaquinariaService maquinariaService) {
        this.maquinariaService = maquinariaService;
    }

    @GetMapping
    public List<MaquinariaResponse> listar(@RequestParam(required = false) EstadoMaquinaria estado,
                                           @RequestParam(required = false) Boolean mantenimientoVencido) {
        return maquinariaService.listar(estado, mantenimientoVencido);
    }

    @GetMapping("/{id}")
    public MaquinariaResponse obtenerPorId(@PathVariable Long id) {
        return maquinariaService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaquinariaResponse> crear(@Valid @RequestBody MaquinariaRequest request) {
        MaquinariaResponse creada = maquinariaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MaquinariaResponse actualizar(@PathVariable Long id,
                                         @Valid @RequestBody MaquinariaRequest request) {
        return maquinariaService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    public MaquinariaResponse cambiarEstado(@PathVariable Long id,
                                            @Valid @RequestBody CambioEstadoMaquinariaRequest request,
                                            Authentication authentication) {
        boolean esAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return maquinariaService.cambiarEstado(id, request.getEstado(), esAdmin);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        maquinariaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
