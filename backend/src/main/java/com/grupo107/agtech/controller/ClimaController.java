package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.ClimaLoteResponse;
import com.grupo107.agtech.service.ClimaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotes/{loteId}/clima")
public class ClimaController {

    private final ClimaService climaService;

    public ClimaController(ClimaService climaService) {
        this.climaService = climaService;
    }

    @GetMapping
    public ClimaLoteResponse obtenerClimaActual(@PathVariable Long loteId) {
        return climaService.obtenerClimaDeLote(loteId);
    }
}
