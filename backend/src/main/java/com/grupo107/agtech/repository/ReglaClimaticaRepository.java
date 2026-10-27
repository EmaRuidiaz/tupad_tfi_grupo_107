package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.ReglaClimatica;
import com.grupo107.agtech.model.TipoLabor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReglaClimaticaRepository extends JpaRepository<ReglaClimatica, Long> {

    List<ReglaClimatica> findAllByOrderByIdAsc();

    Optional<ReglaClimatica> findByTipoLabor(TipoLabor tipoLabor);

    boolean existsByTipoLabor(TipoLabor tipoLabor);

    boolean existsByTipoLaborAndIdNot(TipoLabor tipoLabor, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReglaClimatica r WHERE r.id = :id")
    Optional<ReglaClimatica> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM labor_agricola WHERE tipo_labor = :tipoLabor AND estado IN ('PLANIFICADA', 'APROBADA_CLIMA', 'BLOQUEADA_CLIMA')", nativeQuery = true)
    long contarLaboresPendientesPorTipo(@Param("tipoLabor") String tipoLabor);
}
