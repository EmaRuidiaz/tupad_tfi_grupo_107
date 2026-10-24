package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.MovimientoStock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.List;

@org.springframework.stereotype.Repository
public interface MovimientoStockRepository extends Repository<MovimientoStock, Long> {

    MovimientoStock save(MovimientoStock movimiento);

    @EntityGraph(attributePaths = {"insumo", "usuario"})
    List<MovimientoStock> findByInsumoIdOrderByFechaDescIdDesc(Long insumoId);

    boolean existsByInsumoId(Long insumoId);
}
