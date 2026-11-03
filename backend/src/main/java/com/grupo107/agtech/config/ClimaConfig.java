package com.grupo107.agtech.config;

import com.grupo107.agtech.service.clima.ClimaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra la configuración {@code app.clima.*} de la integración meteorológica (HU-08). */
@Configuration
@EnableConfigurationProperties(ClimaProperties.class)
public class ClimaConfig {
}
