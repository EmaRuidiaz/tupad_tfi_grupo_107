package com.grupo107.agtech.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableroFinancieroResponse {

    private int totalLotes;
    private int lotesConCosecha;
    private int lotesSinCosecha;
    private BigDecimal superficieTotalHa;
    private BigDecimal superficieCosechadaHa;
    private int totalLaboresCompletadas;

    private BigDecimal costosTotalesAcumulados;
    private BigDecimal costoInsumosTotal;
    private BigDecimal costoMaquinariaTotal;
    private BigDecimal costoPromedioPorHa;
    private BigDecimal costosLotesConCosecha;

    private BigDecimal ingresosBrutosTotales;
    private BigDecimal toneladasTotalesCosechadas;
    private BigDecimal rindePromedioTonPorHa;

    private BigDecimal margenNetoTotal;
    private BigDecimal margenNetoLotesCosechados;
    private BigDecimal roiGlobalPct;
    private BigDecimal roiLotesCosechadosPct;

    private List<ReporteFinancieroLoteResponse> lotes;
}
