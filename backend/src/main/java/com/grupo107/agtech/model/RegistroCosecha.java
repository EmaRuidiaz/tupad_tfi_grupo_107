package com.grupo107.agtech.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "registro_cosecha")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroCosecha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "labor_id", unique = true)
    private LaborAgricola labor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cultivo_id", nullable = false)
    private Cultivo cultivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_cosecha", nullable = false)
    private LocalDate fechaCosecha;

    @Column(name = "toneladas_totales", nullable = false, precision = 10, scale = 2)
    private BigDecimal toneladasTotales;

    @Column(name = "rinde_ton_por_ha", nullable = false, precision = 8, scale = 2)
    private BigDecimal rindeTonPorHa;

    @Column(name = "precio_venta_por_ton", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVentaPorTon;

    @Column(name = "ingreso_bruto_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal ingresoBrutoTotal;

    @Column(name = "margen_neto_calculado", nullable = false, precision = 14, scale = 2)
    private BigDecimal margenNetoCalculado = BigDecimal.ZERO;

    /**
     * Calcula el ROI (%) de la cosecha respecto de los costos totales acumulados informados:
     * ROI (%) = (Margen Neto / Costos Totales Acumulados) * 100.
     * Retorna {@code null} si los costos son menores o iguales a 0 (RF-08, criterio 3).
     */
    public BigDecimal calcularRoi(BigDecimal costosTotalesAcumulados) {
        if (costosTotalesAcumulados == null || costosTotalesAcumulados.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal ingreso = this.ingresoBrutoTotal != null ? this.ingresoBrutoTotal : BigDecimal.ZERO;
        BigDecimal margen = ingreso.subtract(costosTotalesAcumulados);
        return margen.multiply(BigDecimal.valueOf(100))
                .divide(costosTotalesAcumulados, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el ROI (%) a partir del ingreso bruto y el margen neto persistidos en el registro.
     */
    public BigDecimal calcularRoi() {
        if (this.ingresoBrutoTotal == null || this.margenNetoCalculado == null) {
            return null;
        }
        BigDecimal costosPersistidos = this.ingresoBrutoTotal.subtract(this.margenNetoCalculado);
        return calcularRoi(costosPersistidos);
    }
}
