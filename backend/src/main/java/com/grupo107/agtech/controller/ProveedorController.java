package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.ProveedorResponse;
import com.grupo107.agtech.service.CompraService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de proveedores (sólo lectura): alimenta el selector del formulario de compras (CU-02). */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final CompraService compraService;

    public ProveedorController(CompraService compraService) {
        this.compraService = compraService;
    }

    @GetMapping
    public List<ProveedorResponse> listar() {
        return compraService.listarProveedores();
    }
}