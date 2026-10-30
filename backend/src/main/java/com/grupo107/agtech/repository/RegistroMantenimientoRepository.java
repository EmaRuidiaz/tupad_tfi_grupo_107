package com.grupo107.agtech.repository;

import com.grupo107.agtech.model.RegistroMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroMantenimientoRepository extends JpaRepository<RegistroMantenimiento, Long> {

    List<RegistroMantenimiento> findByMaquinariaIdOrderByFechaServiceDescIdDesc(Long maquinariaId);

    Optional<RegistroMantenimiento> findFirstByMaquinariaIdOrderByHorasMaquinaEnServiceDescIdDesc(Long maquinariaId);
}
