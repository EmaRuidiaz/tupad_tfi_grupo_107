package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.CultivoResponse;
import com.grupo107.agtech.service.CosechaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de cultivos (sólo lectura): alimenta el selector del formulario de cosecha (HU-12). */
@RestController
@RequestMapping("/api/cultivos")
public class CultivoController {

    private final CosechaService cosechaService;

    public CultivoController(CosechaService cosechaService) {
        this.cosechaService = cosechaService;
    }

    @GetMapping
    public List<CultivoResponse> listar() {
        return cosechaService.listarCultivos();
    }
}
