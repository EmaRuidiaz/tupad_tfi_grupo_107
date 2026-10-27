package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.CambioEstadoLoteRequest;
import com.grupo107.agtech.controller.dto.LoteRequest;
import com.grupo107.agtech.controller.dto.LoteResponse;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.service.LoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    @GetMapping
    public List<LoteResponse> listar(@RequestParam(required = false) EstadoLote estado) {
        return loteService.listarTodos(estado);
    }

    @GetMapping("/{id}")
    public LoteResponse obtenerPorId(@PathVariable Long id) {
        return loteService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public ResponseEntity<LoteResponse> crear(@Valid @RequestBody LoteRequest request,
                                              Authentication authentication) {
        LoteResponse creado = loteService.crear(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public LoteResponse actualizar(@PathVariable Long id,
                                   @Valid @RequestBody LoteRequest request) {
        return loteService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        loteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public LoteResponse cambiarEstado(@PathVariable Long id,
                                      @Valid @RequestBody CambioEstadoLoteRequest request) {
        return loteService.cambiarEstado(id, request);
    }
}
