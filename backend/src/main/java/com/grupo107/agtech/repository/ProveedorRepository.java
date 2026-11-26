package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findAllByOrderByNombreAsc();
}