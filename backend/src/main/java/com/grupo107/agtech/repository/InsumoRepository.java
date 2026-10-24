package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.Insumo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    List<Insumo> findAllByOrderByIdAsc();

    /**
     * Obtiene el insumo aplicando un bloqueo pesimista de escritura ({@code SELECT ... FOR UPDATE})
     * para evitar condiciones de carrera (lost updates) ante ajustes o consumos simultáneos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Insumo i WHERE i.id = :id")
    Optional<Insumo> findByIdForUpdate(@Param("id") Long id);
}
