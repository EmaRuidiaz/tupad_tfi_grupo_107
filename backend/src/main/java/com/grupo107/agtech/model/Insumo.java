package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "insumo")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CategoriaInsumo categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private UnidadMedida unidadMedida;

    @Column(name = "stock_actual", nullable = false, precision = 12, scale = 2)
    private BigDecimal stockActual = BigDecimal.ZERO;

    @Column(name = "stock_reservado", nullable = false, precision = 12, scale = 2)
    private BigDecimal stockReservado = BigDecimal.ZERO;

    @Column(name = "stock_minimo_alerta", nullable = false, precision = 12, scale = 2)
    private BigDecimal stockMinimoAlerta = BigDecimal.ZERO;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    public BigDecimal getStockDisponible() {
        return stockActual.subtract(stockReservado);
    }

    public boolean isStockCritico() {
        return stockActual.compareTo(stockMinimoAlerta) <= 0;
    }

    public void reservar(BigDecimal cantidad) {
        if (cantidad != null && cantidad.compareTo(BigDecimal.ZERO) > 0) {
            this.stockReservado = this.stockReservado.add(cantidad);
        }
    }

    public void liberarReserva(BigDecimal cantidad) {
        if (cantidad != null && cantidad.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal nuevo = this.stockReservado.subtract(cantidad);
            this.stockReservado = nuevo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : nuevo;
        }
    }
}
