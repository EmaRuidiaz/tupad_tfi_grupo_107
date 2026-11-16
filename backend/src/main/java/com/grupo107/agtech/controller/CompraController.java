package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.CompraRequest;
import com.grupo107.agtech.controller.dto.CompraResponse;
import com.grupo107.agtech.service.CompraService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @GetMapping
    public List<CompraResponse> listar() {
        return compraService.listar();
    }

    @GetMapping("/{id}")
    public CompraResponse obtener(@PathVariable Long id) {
        return compraService.obtenerPorId(id);
    }

    // Matriz de permisos (doc 07, sección 7.2): registrar compras solo ADMIN.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompraResponse> registrar(@Valid @RequestBody CompraRequest request,
                                                    Authentication authentication) {
        CompraResponse creada = compraService.registrar(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }
}