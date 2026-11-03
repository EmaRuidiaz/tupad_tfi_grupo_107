package com.grupo107.agtech.service.clima;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Adaptador de {@link ProveedorClima} que aplica la estrategia de resiliencia del doc 07, sección 7.3:
 * timeout (en {@link OpenMeteoApi}), reintentos con espera exponencial, circuit breaker y una caché
 * de contingencia. Si la API falla se usa la última lectura de las coordenadas mientras tenga menos
 * de 30 minutos; si no hay, se lanza {@link ClimaNoDisponibleException}.
 */
@Component
public class ClimaClient implements ProveedorClima {

    private static final Logger log = LoggerFactory.getLogger(ClimaClient.class);
    private static final String NOMBRE = "clima";

    private final OpenMeteoApi api;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Cache<String, ConsultaClima> cache;

    public ClimaClient(OpenMeteoApi api, ClimaProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        this.api = api;

        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.ventanaCircuito())
                .minimumNumberOfCalls(properties.ventanaCircuito())
                .failureRateThreshold(properties.umbralFallasPct())
                .waitDurationInOpenState(properties.circuitoAbierto())
                .permittedNumberOfCallsInHalfOpenState(properties.llamadasSemiabierto())
                .build());
        this.circuitBreaker = circuitBreakers.circuitBreaker(NOMBRE);
        this.circuitBreaker.getEventPublisher().onStateTransition(evento ->
                log.warn("Circuit breaker de la API climática: {}", evento.getStateTransition()));

        RetryRegistry reintentos = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(properties.reintentos() + 1)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.esperaReintento(), 2))
                .retryOnException(ClimaClient::esFallaTransitoria)
                .build());
        this.retry = reintentos.retry(NOMBRE);

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.vigenciaCache())
                .maximumSize(1_000)
                .recordStats()
                .build();

        // Métricas para Actuator (doc 09): estado del circuito, reintentos y aciertos de caché.
        meterRegistry.ifAvailable(registry -> {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(registry);
            TaggedRetryMetrics.ofRetryRegistry(reintentos).bindTo(registry);
            CaffeineCacheMetrics.monitor(registry, cache, NOMBRE);
        });
    }

    @Override
    public ConsultaClima obtenerClimaActual(BigDecimal latitud, BigDecimal longitud) {
        String clave = claveCache(latitud, longitud);
        Supplier<LecturaClima> llamada = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> api.consultar(latitud, longitud)));

        try {
            ConsultaClima consulta = new ConsultaClima(llamada.get(), OrigenClima.API, Instant.now());
            cache.put(clave, consulta);
            return consulta;
        } catch (RuntimeException ex) {
            ConsultaClima enCache = cache.getIfPresent(clave);
            if (enCache != null) {
                log.warn("API climática no disponible ({}); se usa la lectura en caché de {} para {}",
                        ex.getClass().getSimpleName(), enCache.obtenidaEn(), clave);
                return new ConsultaClima(enCache.lectura(), OrigenClima.CACHE, enCache.obtenidaEn());
            }
            log.error("API climática no disponible y sin caché vigente para {}: {}", clave, ex.toString());
            throw new ClimaNoDisponibleException(mensajeNoDisponible(ex), ex);
        }
    }

    /** Vuelve el circuito a CLOSED y vacía la caché. Pensado para aislar los tests entre sí. */
    public void reiniciar() {
        circuitBreaker.reset();
        cache.invalidateAll();
    }

    CircuitBreaker.State estadoCircuito() {
        return circuitBreaker.getState();
    }

    // Coordenadas redondeadas a 2 decimales (~1 km): lotes vecinos comparten la lectura.
    private static String claveCache(BigDecimal latitud, BigDecimal longitud) {
        return latitud.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "," + longitud.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // Sólo se reintentan errores de red/timeout, HTTP 5xx y 429 (doc 07, sección 7.3).
    private static boolean esFallaTransitoria(Throwable ex) {
        return ex instanceof ResourceAccessException
                || ex instanceof HttpServerErrorException
                || ex instanceof HttpClientErrorException.TooManyRequests;
    }

    private static String mensajeNoDisponible(RuntimeException ex) {
        String motivo = ex instanceof CallNotPermittedException
                ? "la consulta al proveedor está suspendida temporalmente por fallas repetidas"
                : "el proveedor meteorológico no respondió";
        return "Validación climática no disponible: " + motivo
                + " y no hay datos recientes en caché. Reintentá en unos minutos.";
    }
}
