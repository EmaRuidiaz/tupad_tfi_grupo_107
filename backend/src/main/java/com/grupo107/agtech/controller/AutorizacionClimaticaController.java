package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.AutorizacionManualRequest;
import com.grupo107.agtech.controller.dto.ValidacionClimaResponse;
import com.grupo107.agtech.service.AutorizacionClimaticaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Autorización climática de labores (HU-10, CU-04, RN-01 y RN-11). */
@RestController
@RequestMapping("/api/labores/{laborId}")
public class AutorizacionClimaticaController {

    private final AutorizacionClimaticaService autorizacionClimaticaService;

    public AutorizacionClimaticaController(AutorizacionClimaticaService autorizacionClimaticaService) {
        this.autorizacionClimaticaService = autorizacionClimaticaService;
    }

    @PostMapping("/validar-clima")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO', 'OPERARIO')")
    public ValidacionClimaResponse validarClima(@PathVariable Long laborId) {
        return autorizacionClimaticaService.validarClima(laborId);
    }

    @PostMapping("/autorizacion-manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public ValidacionClimaResponse autorizarManualmente(
            @PathVariable Long laborId,
            @Valid @RequestBody AutorizacionManualRequest request,
            Authentication auth) {
        return autorizacionClimaticaService.autorizarManualmente(laborId, request.getJustificacion(), auth.getName());
    }
}
