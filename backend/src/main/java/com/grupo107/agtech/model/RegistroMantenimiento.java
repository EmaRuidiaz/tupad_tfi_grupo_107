package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "registro_mantenimiento")
@Data
@NoArgsConstructor
public class RegistroMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maquinaria_id", nullable = false)
    private Maquinaria maquinaria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_service", nullable = false)
    private LocalDate fechaService;

    @Column(name = "horas_maquina_en_service", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasMaquinaEnService;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_mantenimiento", nullable = false, length = 40)
    private TipoMantenimiento tipoMantenimiento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costo = BigDecimal.ZERO;

    @Column(name = "proximo_service_horas", nullable = false, precision = 10, scale = 2)
    private BigDecimal proximoServiceHoras;

    @Column(columnDefinition = "TEXT")
    private String descripcion;
}
