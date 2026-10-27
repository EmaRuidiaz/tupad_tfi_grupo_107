package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.EstadoMaquinaria;
import com.grupo107.agtech.model.Maquinaria;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaquinariaRepository extends JpaRepository<Maquinaria, Long> {

    List<Maquinaria> findAllByOrderByIdAsc();

    List<Maquinaria> findByEstadoOrderByIdAsc(EstadoMaquinaria estado);

    boolean existsByMatriculaOSerieIgnoreCase(String matriculaOSerie);

    boolean existsByMatriculaOSerieIgnoreCaseAndIdNot(String matriculaOSerie, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Maquinaria m WHERE m.id = :id")
    Optional<Maquinaria> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM registro_mantenimiento WHERE maquinaria_id = :id", nativeQuery = true)
    long contarMantenimientos(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM labor_agricola WHERE maquinaria_id = :id", nativeQuery = true)
    long contarLabores(@Param("id") Long id);

    @Query(value = "SELECT COALESCE(MAX(horas_maquina_en_service), 0) FROM registro_mantenimiento WHERE maquinaria_id = :id", nativeQuery = true)
    BigDecimal obtenerMaxHorasService(@Param("id") Long id);
}
