package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.Cultivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CultivoRepository extends JpaRepository<Cultivo, Long> {

    List<Cultivo> findAllByOrderByNombreAsc();

    Optional<Cultivo> findByNombreIgnoreCase(String nombre);
}
