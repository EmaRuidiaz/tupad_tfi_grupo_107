package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.Maquinaria;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaquinariaRepository extends JpaRepository<Maquinaria, Long> {

    List<Maquinaria> findAllByOrderByIdAsc();

    boolean existsByMatriculaOSerieIgnoreCase(String matriculaOSerie);

    boolean existsByMatriculaOSerieIgnoreCaseAndIdNot(String matriculaOSerie, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Maquinaria m WHERE m.id = :id")
    Optional<Maquinaria> findByIdForUpdate(@Param("id") Long id);
}
