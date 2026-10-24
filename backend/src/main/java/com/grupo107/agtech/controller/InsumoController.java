package com.grupo107.agtech.controller;

import com.grupo107.agtech.controller.dto.AjusteStockRequest;
import com.grupo107.agtech.controller.dto.InsumoRequest;
import com.grupo107.agtech.controller.dto.InsumoResponse;
import com.grupo107.agtech.controller.dto.MovimientoStockResponse;
import com.grupo107.agtech.service.InsumoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST para la gestión del catálogo de insumos, alertas de stock mínimo
 * y auditoría de movimientos/ajustes de stock (HU-04, RF-03, RN-04, RN-09).
 *
 * Permisos según doc 07 (sección 7.2):
 * - Consulta de insumos e historial de movimientos: ADMIN, AGRONOMO, OPERARIO.
 * - Alta, modificación, baja y ajustes de stock: sólo ADMIN.
 */
@RestController
@RequestMapping("/api/insumos")
public class InsumoController {

    private final InsumoService insumoService;

    public InsumoController(InsumoService insumoService) {
        this.insumoService = insumoService;
    }

    @GetMapping
    public List<InsumoResponse> listar(@RequestParam(required = false) Boolean stockCritico) {
        return insumoService.listarTodos(stockCritico);
    }

    @GetMapping("/{id}")
    public InsumoResponse obtenerPorId(@PathVariable Long id) {
        return insumoService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InsumoResponse> crear(@Valid @RequestBody InsumoRequest request,
                                                Authentication authentication) {
        InsumoResponse creado = insumoService.crear(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InsumoResponse actualizar(@PathVariable Long id,
                                     @Valid @RequestBody InsumoRequest request) {
        return insumoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        insumoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ajustes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovimientoStockResponse> registrarAjuste(@PathVariable Long id,
                                                                   @Valid @RequestBody AjusteStockRequest request,
                                                                   Authentication authentication) {
        MovimientoStockResponse mov = insumoService.registrarAjuste(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(mov);
    }

    @GetMapping("/{id}/movimientos")
    public List<MovimientoStockResponse> listarMovimientos(@PathVariable Long id) {
        return insumoService.listarMovimientos(id);
    }
}
