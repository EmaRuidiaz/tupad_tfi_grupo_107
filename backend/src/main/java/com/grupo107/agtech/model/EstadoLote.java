package com.grupo107.agtech.model;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoLote {
    EN_PREPARACION,
    SEMBRADO,
    EN_CRECIMIENTO,
    LISTO_COSECHA,
    COSECHADO,
    DESCANSO;

    public Set<EstadoLote> transicionesManuales() {
        return switch (this) {
            case SEMBRADO -> EnumSet.of(EN_CRECIMIENTO, EN_PREPARACION);
            case EN_CRECIMIENTO -> EnumSet.of(LISTO_COSECHA, EN_PREPARACION);
            case COSECHADO -> EnumSet.of(EN_PREPARACION, DESCANSO);
            case DESCANSO -> EnumSet.of(EN_PREPARACION);
            case EN_PREPARACION, LISTO_COSECHA -> EnumSet.noneOf(EstadoLote.class);
        };
    }

    public boolean esPerdidaTotal(EstadoLote destino) {
        return (this == SEMBRADO || this == EN_CRECIMIENTO) && destino == EN_PREPARACION;
    }
}
