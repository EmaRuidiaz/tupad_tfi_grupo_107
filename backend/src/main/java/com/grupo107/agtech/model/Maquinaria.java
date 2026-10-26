package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "maquinaria")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Maquinaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoMaquinaria tipo;

    @Column(name = "matricula_o_serie", nullable = false, unique = true, length = 50)
    private String matriculaOSerie;

    @Column(name = "horas_uso_actuales", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasUsoActuales = BigDecimal.ZERO;

    @Column(name = "horas_para_proximo_service", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasParaProximoService = BigDecimal.ZERO;

    @Column(name = "consumo_litros_hora", nullable = false, precision = 8, scale = 2)
    private BigDecimal consumoLitrosHora = BigDecimal.ZERO;

    @Column(name = "costo_operativo_hora", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoOperativoHora = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoMaquinaria estado = EstadoMaquinaria.OPERATIVO;

    public BigDecimal getHorasRestantesService() {
        return horasParaProximoService.subtract(horasUsoActuales);
    }

    public boolean isMantenimientoVencido() {
        return horasUsoActuales.compareTo(horasParaProximoService) >= 0;
    }

    public boolean requiereService() {
        return isMantenimientoVencido();
    }

    public void sumarHoras(BigDecimal horas) {
        if (horas != null && horas.compareTo(BigDecimal.ZERO) > 0) {
            this.horasUsoActuales = this.horasUsoActuales.add(horas);
        }
    }
}
