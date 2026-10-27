package com.grupo107.agtech.model;

/**
 * Tipos de labor agrícola (doc 03, secciones 3.8 y 3.9; RN-01).
 * Las labores de LABRANZA no llevan regla climática asociada.
 */
public enum TipoLabor {
    PULVERIZACION,
    SIEMBRA,
    COSECHA,
    FERTILIZACION,
    LABRANZA;

    public boolean requiereValidacionClimatica() {
        return this != LABRANZA;
    }
}
