package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.TipoLabor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class LaborRequest {

    @NotNull(message = "El lote es obligatorio")
    private Long loteId;

    private Long maquinariaId;

    @NotNull(message = "El tipo de labor es obligatorio")
    private TipoLabor tipoLabor;

    @NotNull(message = "La fecha planificada es obligatoria")
    private LocalDateTime fechaPlanificada;

    @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
    private String observaciones;

    @Valid
    private List<LaborInsumoRequest> insumos = new ArrayList<>();
}
