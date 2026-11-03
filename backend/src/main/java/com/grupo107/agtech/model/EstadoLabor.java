package com.grupo107.agtech.model;

public enum EstadoLabor {
    PLANIFICADA,
    APROBADA_CLIMA,
    BLOQUEADA_CLIMA,
    EN_EJECUCION,
    COMPLETADA,
    CANCELADA;

    public boolean admiteReprogramacionOCancelacion() {
        return this == PLANIFICADA || this == APROBADA_CLIMA || this == BLOQUEADA_CLIMA;
    }
}
