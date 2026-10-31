package com.grupo107.agtech.service.clima;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de la estrategia de resiliencia (doc 07, sección 7.3). */
class ClimaClientTests {

    private static final BigDecimal LAT = new BigDecimal("-33.892000");
    private static final BigDecimal LON = new BigDecimal("-60.573000");

    private OpenMeteoApi api;
    private ClimaClient cliente;

    @BeforeEach
    void setUp() {
        api = mock(OpenMeteoApi.class);
        ClimaProperties properties = new ClimaProperties(
                "http://localhost", Duration.ofSeconds(3), 2, Duration.ofMillis(1),
                10, 50, Duration.ofSeconds(60), 3, Duration.ofMinutes(30));
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> sinMetricas = mock(ObjectProvider.class);
        cliente = new ClimaClient(api, properties, sinMetricas);
    }

    private static LecturaClima lectura() {
        return new LecturaClima(new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("5"),
                new BigDecimal("60"), BigDecimal.ZERO, LocalDateTime.of(2026, 10, 31, 12, 0));
    }

    @Test
    void reintentaFallasDeRedHastaDosVeces() {
        when(api.consultar(any(), any()))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(lectura());

        ConsultaClima consulta = cliente.obtenerClimaActual(LAT, LON);

        assertEquals(OrigenClima.API, consulta.origen());
        verify(api, times(3)).consultar(LAT, LON);
    }

    @Test
    void reintentaErrores5xxY429PeroNoOtros4xx() {
        when(api.consultar(any(), any()))
                .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null))
                .thenReturn(lectura());
        assertEquals(OrigenClima.API, cliente.obtenerClimaActual(LAT, LON).origen());
        verify(api, times(3)).consultar(any(), any());

        // Sin caché, para que el 400 no se resuelva con la lectura anterior.
        cliente.reiniciar();
        reset(api);
        when(api.consultar(any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null, null, null));
        assertThrows(ClimaNoDisponibleException.class, () -> cliente.obtenerClimaActual(LAT, LON));
        verify(api, times(1)).consultar(any(), any());
    }

    @Test
    void elCircuitoSeAbreTrasFallasRepetidasYDejaDeConsultarLaApi() {
        when(api.consultar(any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null, null, null));

        for (int i = 0; i < 10; i++) {
            assertThrows(ClimaNoDisponibleException.class, () -> cliente.obtenerClimaActual(LAT, LON));
        }
        assertEquals(CircuitBreaker.State.OPEN, cliente.estadoCircuito());

        ClimaNoDisponibleException ex = assertThrows(ClimaNoDisponibleException.class,
                () -> cliente.obtenerClimaActual(LAT, LON));
        assertTrue(ex.getMessage().contains("suspendida"));
        verify(api, times(10)).consultar(any(), any());
    }

    @Test
    void siLaApiFallaRespondeConLaCacheDeCoordenadasCercanas() {
        when(api.consultar(any(), any())).thenReturn(lectura());
        ConsultaClima original = cliente.obtenerClimaActual(LAT, LON);

        reset(api);
        when(api.consultar(any(), any())).thenThrow(new ResourceAccessException("caída"));

        // -33.894 / -60.571 redondean a la misma clave (-33.89, -60.57).
        ConsultaClima vecina = cliente.obtenerClimaActual(new BigDecimal("-33.894"), new BigDecimal("-60.571"));

        assertEquals(OrigenClima.CACHE, vecina.origen());
        assertEquals(original.obtenidaEn(), vecina.obtenidaEn());
        assertEquals(original.lectura(), vecina.lectura());
    }
}
