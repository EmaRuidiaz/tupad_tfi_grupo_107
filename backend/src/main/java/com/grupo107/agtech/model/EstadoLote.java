package com.grupo107.agtech.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados agronómicos del lote y sus transiciones (doc 06, sección 6.3.1 - RN-08).
 */
public enum EstadoLote {
    EN_PREPARACION,
    SEMBRADO,
    EN_CRECIMIENTO,
    LISTO_COSECHA,
    COSECHADO,
    DESCANSO;

    /**
     * Transiciones que un ADMIN o AGRONOMO puede registrar manualmente.
     * EN_PREPARACION -> SEMBRADO y LISTO_COSECHA -> COSECHADO no figuran porque las realiza
     * el sistema al completar una labor de siembra o al registrar una cosecha.
     */
    public Set<EstadoLote> transicionesManuales() {
        return switch (this) {
            case SEMBRADO -> EnumSet.of(EN_CRECIMIENTO, EN_PREPARACION);
            case EN_CRECIMIENTO -> EnumSet.of(LISTO_COSECHA, EN_PREPARACION);
            case COSECHADO -> EnumSet.of(EN_PREPARACION, DESCANSO);
            case DESCANSO -> EnumSet.of(EN_PREPARACION);
            case EN_PREPARACION, LISTO_COSECHA -> EnumSet.noneOf(EstadoLote.class);
        };
    }

    /** Volver a EN_PREPARACION con el cultivo implantado representa una pérdida total. */
    public boolean esPerdidaTotal(EstadoLote destino) {
        return (this == SEMBRADO || this == EN_CRECIMIENTO) && destino == EN_PREPARACION;
    }
}
