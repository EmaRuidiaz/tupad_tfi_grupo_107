package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "labor_insumo")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaborInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "labor_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LaborAgricola labor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(name = "cantidad_utilizada", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadUtilizada;

    @Column(name = "costo_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoSubtotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_reserva", nullable = false, length = 20)
    private EstadoReserva estadoReserva = EstadoReserva.RESERVADO;
}
