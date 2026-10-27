package com.grupo107.agtech.model;

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
