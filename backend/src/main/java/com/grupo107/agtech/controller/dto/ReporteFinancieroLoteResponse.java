package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteFinancieroLoteResponse {

    private Long loteId;
    private String loteNombre;
    private BigDecimal superficieHa;
    private EstadoLote estadoLote;
    private String tipoSuelo;

    private int cantidadLaboresCompletadas;
    private int cantidadLaboresPendientes;
    private BigDecimal horasMaquinaTotales;
    private BigDecimal costoInsumosAcumulado;
    private BigDecimal costoMaquinariaAcumulado;
    private BigDecimal costosTotalesAcumulados;
    private BigDecimal costoPorHa;

    private boolean cosechaRegistrada;
    private int cantidadCosechas;
    private String cultivoNombre;
    private String cultivoEspecie;
    private LocalDate fechaUltimaCosecha;
    private BigDecimal toneladasTotales;
    private BigDecimal rindeTonPorHa;
    private BigDecimal precioVentaPromedioPorTon;
    private BigDecimal ingresoBrutoTotal;
    private BigDecimal ingresoPorHa;
    private BigDecimal margenNeto;
    private BigDecimal margenPorHa;

    private BigDecimal roiPct;
    private String estadoRoi;
    private String mensajeRoi;

    private List<CostoLaborResumenResponse> laboresCompletadas;
    private List<CosechaResumenResponse> cosechas;
}
