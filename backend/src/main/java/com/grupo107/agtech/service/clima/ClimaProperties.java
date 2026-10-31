package com.grupo107.agtech.service.clima;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración de la integración climática ({@code app.clima.*}). Los valores por defecto
 * de application.yml siguen la tabla de resiliencia del doc 07, sección 7.3.
 *
 * @param reintentos cantidad de reintentos además del primer intento
 */
@ConfigurationProperties(prefix = "app.clima")
public record ClimaProperties(
        String baseUrl,
        Duration timeout,
        int reintentos,
        Duration esperaReintento,
        int ventanaCircuito,
        float umbralFallasPct,
        Duration circuitoAbierto,
        int llamadasSemiabierto,
        Duration vigenciaCache
) {
}
