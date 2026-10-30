package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.LaborAgricola;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LaborAgricolaRepository extends JpaRepository<LaborAgricola, Long> {

    List<LaborAgricola> findAllByOrderByFechaPlanificadaDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LaborAgricola l WHERE l.id = :id")
    Optional<LaborAgricola> findByIdForUpdate(@Param("id") Long id);
}
