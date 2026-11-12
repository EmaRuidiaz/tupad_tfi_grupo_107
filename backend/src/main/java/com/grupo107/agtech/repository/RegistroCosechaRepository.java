package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.RegistroCosecha;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroCosechaRepository extends JpaRepository<RegistroCosecha, Long> {

    @EntityGraph(attributePaths = {"lote", "cultivo", "usuario", "labor"})
    Optional<RegistroCosecha> findConDetalleById(Long id);

    /** Cosecha más reciente del lote: su fecha abre la ventana de costos del ciclo siguiente (RN-12). */
    Optional<RegistroCosecha> findFirstByLoteIdOrderByFechaCosechaDescIdDesc(Long loteId);

    /** labor_id es UNIQUE: una orden de trilla se vincula a una sola cosecha. */
    boolean existsByLaborId(Long laborId);

    @EntityGraph(attributePaths = {"lote", "cultivo", "usuario", "labor"})
    List<RegistroCosecha> findAllByOrderByFechaCosechaDescIdDesc();

    @EntityGraph(attributePaths = {"lote", "cultivo", "usuario", "labor"})
    List<RegistroCosecha> findByLoteIdOrderByFechaCosechaDescIdDesc(Long loteId);
}
