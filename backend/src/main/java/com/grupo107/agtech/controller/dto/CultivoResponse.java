package com.grupo107.agtech.controller.dto;

import com.grupo107.agtech.model.Cultivo;

public record CultivoResponse(Long id, String nombre, String especie) {

    public static CultivoResponse from(Cultivo cultivo) {
        return new CultivoResponse(cultivo.getId(), cultivo.getNombre(), cultivo.getEspecie());
    }
}
