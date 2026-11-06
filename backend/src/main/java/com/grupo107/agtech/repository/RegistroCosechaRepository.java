package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.RegistroCosecha;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistroCosechaRepository extends JpaRepository<RegistroCosecha, Long> {

    @EntityGraph(attributePaths = {"lote", "cultivo", "usuario", "labor"})
    List<RegistroCosecha> findAllByOrderByFechaCosechaDescIdDesc();

    @EntityGraph(attributePaths = {"lote", "cultivo", "usuario", "labor"})
    List<RegistroCosecha> findByLoteIdOrderByFechaCosechaDescIdDesc(Long loteId);
}
