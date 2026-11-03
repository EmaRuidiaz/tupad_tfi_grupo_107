package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.LaborAgricola;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LaborAgricolaRepository extends JpaRepository<LaborAgricola, Long> {

    @EntityGraph(attributePaths = {"lote", "maquinaria", "usuario", "insumos", "insumos.insumo"})
    List<LaborAgricola> findAllByOrderByFechaPlanificadaDescIdDesc();

    @EntityGraph(attributePaths = {"lote", "maquinaria", "usuario", "insumos", "insumos.insumo"})
    Optional<LaborAgricola> findConDetalleById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LaborAgricola l WHERE l.id = :id")
    Optional<LaborAgricola> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT l FROM LaborAgricola l
            JOIN FETCH l.lote
            WHERE l.maquinaria.id = :maquinariaId
              AND l.estado IN :estadosPendientes
              AND l.fechaPlanificada BETWEEN :desde AND :hasta
              AND (:excluirId IS NULL OR l.id <> :excluirId)
            ORDER BY l.fechaPlanificada ASC
            """)
    List<LaborAgricola> buscarConflictosHorariosMaquinaria(
            @Param("maquinariaId") Long maquinariaId,
            @Param("estadosPendientes") Collection<EstadoLabor> estadosPendientes,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("excluirId") Long excluirId);
}
