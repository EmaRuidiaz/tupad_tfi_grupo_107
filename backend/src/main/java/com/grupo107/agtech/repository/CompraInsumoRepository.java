package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.CompraInsumo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompraInsumoRepository extends JpaRepository<CompraInsumo, Long> {

    @EntityGraph(attributePaths = {"proveedor", "usuario", "detalles", "detalles.insumo"})
    List<CompraInsumo> findAllByOrderByFechaCompraDescIdDesc();

    @EntityGraph(attributePaths = {"proveedor", "usuario", "detalles", "detalles.insumo"})
    Optional<CompraInsumo> findConDetalleById(Long id);
}