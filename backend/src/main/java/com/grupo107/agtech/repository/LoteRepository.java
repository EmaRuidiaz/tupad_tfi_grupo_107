package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.Lote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {

    List<Lote> findAllByOrderByIdAsc();

    List<Lote> findByEstadoOrderByIdAsc(EstadoLote estado);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.id = :id")
    Optional<Lote> findByIdForUpdate(@Param("id") Long id);

    // Labores y cosechas todavía no tienen entidad JPA: se consultan con SQL nativo.
    @Query(value = "SELECT COUNT(*) FROM labor_agricola WHERE lote_id = :id", nativeQuery = true)
    long contarLabores(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM registro_cosecha WHERE lote_id = :id", nativeQuery = true)
    long contarCosechas(@Param("id") Long id);
}
