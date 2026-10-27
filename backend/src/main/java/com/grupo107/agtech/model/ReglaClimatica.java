package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "regla_climatica")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReglaClimatica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_labor", nullable = false, unique = true, length = 40)
    private TipoLabor tipoLabor;

    @Column(name = "viento_max_kmh", nullable = false, precision = 5, scale = 2)
    private BigDecimal vientoMaxKmh;

    @Column(name = "viento_min_kmh", nullable = false, precision = 5, scale = 2)
    private BigDecimal vientoMinKmh = BigDecimal.ZERO;

    @Column(name = "temp_min_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal tempMinC = new BigDecimal("5.00");

    @Column(name = "temp_max_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal tempMaxC = new BigDecimal("35.00");

    @Column(name = "prob_lluvia_max_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal probLluviaMaxPct = new BigDecimal("20.00");

    @Column(name = "humedad_min_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal humedadMinPct = new BigDecimal("30.00");

    @Column(name = "mensaje_alerta", nullable = false, length = 255)
    private String mensajeAlerta;
}
