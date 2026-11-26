package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.ProveedorRequest;
import com.grupo107.agtech.controller.dto.ProveedorResponse;
import com.grupo107.agtech.model.Proveedor;
import com.grupo107.agtech.repository.ProveedorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public ProveedorService(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listar() {
        return proveedorRepository.findAllByOrderByNombreAsc().stream()
                .map(ProveedorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProveedorResponse obtenerPorId(Long id) {
        return proveedorRepository.findById(id)
                .map(ProveedorResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado con id: " + id));
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        Proveedor proveedor = new Proveedor();
        aplicarDatos(proveedor, request);
        try {
            Proveedor guardado = proveedorRepository.saveAndFlush(proveedor);
            return ProveedorResponse.from(guardado);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un proveedor con ese CUIT o datos duplicados");
        }
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado con id: " + id));
        aplicarDatos(proveedor, request);
        try {
            Proveedor actualizado = proveedorRepository.saveAndFlush(proveedor);
            return ProveedorResponse.from(actualizado);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe otro proveedor con ese CUIT o datos duplicados");
        }
    }

    @Transactional
    public void eliminar(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado con id: " + id));
        try {
            proveedorRepository.delete(proveedor);
            proveedorRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el proveedor porque tiene compras asociadas");
        }
    }

    private void aplicarDatos(Proveedor proveedor, ProveedorRequest request) {
        proveedor.setNombre(request.getNombre().trim());
        proveedor.setCuit(request.getCuit().trim());
        proveedor.setTelefono(normalizarOpcional(request.getTelefono()));
        proveedor.setEmail(normalizarOpcional(request.getEmail()));
    }

    private String normalizarOpcional(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
