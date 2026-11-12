package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.CosechaRequest;
import com.grupo107.agtech.controller.dto.CosechaResponse;
import com.grupo107.agtech.service.CosechaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU-12: registro y consulta de cosechas. No hay PUT ni DELETE: cada cosecha es el cierre
 * inmutable de un ciclo productivo (RN-04, RN-12).
 */
@RestController
@RequestMapping("/api/cosechas")
public class CosechaController {

    private final CosechaService cosechaService;

    public CosechaController(CosechaService cosechaService) {
        this.cosechaService = cosechaService;
    }

    @GetMapping
    public List<CosechaResponse> listar(@RequestParam(required = false) Long loteId) {
        return cosechaService.listar(loteId);
    }

    @GetMapping("/{id}")
    public CosechaResponse obtener(@PathVariable Long id) {
        return cosechaService.obtenerPorId(id);
    }

    // Matriz de permisos (doc 07, sección 7.2): registrar cosecha sólo ADMIN y AGRONOMO.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public ResponseEntity<CosechaResponse> registrar(@Valid @RequestBody CosechaRequest request,
                                                     Authentication authentication) {
        CosechaResponse creada = cosechaService.registrar(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }
}
