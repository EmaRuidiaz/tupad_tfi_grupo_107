package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "labor_agricola")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaborAgricola {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquinaria_id")
    private Maquinaria maquinaria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_labor", nullable = false, length = 40)
    private TipoLabor tipoLabor;

    @Column(name = "fecha_planificada", nullable = false)
    private LocalDateTime fechaPlanificada;

    @Column(name = "fecha_ejecucion")
    private LocalDateTime fechaEjecucion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoLabor estado = EstadoLabor.PLANIFICADA;

    @Column(name = "horas_maquina_consumidas", precision = 8, scale = 2)
    private BigDecimal horasMaquinaConsumidas = BigDecimal.ZERO;

    @Column(name = "costo_total_calculado", precision = 12, scale = 2)
    private BigDecimal costoTotalCalculado = BigDecimal.ZERO;

    @Column(name = "temperatura_registrada", precision = 5, scale = 2)
    private BigDecimal temperaturaRegistrada;

    @Column(name = "viento_velocidad_kmh", precision = 6, scale = 2)
    private BigDecimal vientoVelocidadKmh;

    @Column(name = "probabilidad_lluvia_pct", precision = 5, scale = 2)
    private BigDecimal probabilidadLluviaPct;

    @Column(name = "humedad_registrada_pct", precision = 5, scale = 2)
    private BigDecimal humedadRegistradaPct;

    @Column(name = "observaciones_climaticas", columnDefinition = "TEXT")
    private String observacionesClimaticas;

    @OneToMany(mappedBy = "labor", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LaborInsumo> insumos = new ArrayList<>();

    public void agregarInsumo(LaborInsumo item) {
        item.setLabor(this);
        this.insumos.add(item);
    }
}
