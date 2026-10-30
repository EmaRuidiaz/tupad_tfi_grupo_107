package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.LaborRequest;
import com.grupo107.agtech.controller.dto.LaborResponse;
import com.grupo107.agtech.controller.dto.ReprogramarLaborRequest;
import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.service.LaborService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/labores")
public class LaborController {

    private final LaborService laborService;

    public LaborController(LaborService laborService) {
        this.laborService = laborService;
    }

    @GetMapping
    public List<LaborResponse> listar(
            @RequestParam(required = false) Long loteId,
            @RequestParam(required = false) EstadoLabor estado,
            @RequestParam(required = false) TipoLabor tipoLabor) {
        return laborService.listar(loteId, estado, tipoLabor);
    }

    @GetMapping("/{id}")
    public LaborResponse obtener(@PathVariable Long id) {
        return laborService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public ResponseEntity<LaborResponse> programar(
            @Valid @RequestBody LaborRequest request,
            Authentication auth) {
        LaborResponse creada = laborService.programar(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PatchMapping("/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public LaborResponse reprogramar(
            @PathVariable Long id,
            @Valid @RequestBody ReprogramarLaborRequest request) {
        return laborService.reprogramar(id, request);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
    public LaborResponse cancelar(@PathVariable Long id) {
        return laborService.cancelar(id);
    }
}
