package com.grupo107.agtech.service.clima;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDateTime;

/**
 * Llamada HTTP a Open-Meteo (https://open-meteo.com, sin API key). Sólo traduce la respuesta:
 * los reintentos, el circuit breaker y la caché los aplica {@link ClimaClient}.
 */
@Component
public class OpenMeteoApi {

    private static final String VARIABLES =
            "temperature_2m,relative_humidity_2m,precipitation,precipitation_probability,wind_speed_10m";

    private final RestClient restClient;

    public OpenMeteoApi(ClimaProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public LecturaClima consultar(BigDecimal latitud, BigDecimal longitud) {
        RespuestaOpenMeteo respuesta = restClient.get()
                .uri(uri -> uri.path("/v1/forecast")
                        .queryParam("latitude", latitud.toPlainString())
                        .queryParam("longitude", longitud.toPlainString())
                        .queryParam("current", VARIABLES)
                        .queryParam("wind_speed_unit", "kmh")
                        .queryParam("timezone", "auto")
                        .build())
                .retrieve()
                .body(RespuestaOpenMeteo.class);

        if (respuesta == null || respuesta.current() == null) {
            throw new IllegalStateException("Open-Meteo devolvió una respuesta sin datos actuales");
        }
        Actual actual = respuesta.current();
        return new LecturaClima(
                actual.temperatura(),
                actual.viento(),
                actual.probabilidadLluvia(),
                actual.humedad(),
                actual.precipitacion(),
                actual.time()
        );
    }

    record RespuestaOpenMeteo(Actual current) {
    }

    record Actual(
            LocalDateTime time,
            @JsonProperty("temperature_2m") BigDecimal temperatura,
            @JsonProperty("relative_humidity_2m") BigDecimal humedad,
            @JsonProperty("precipitation") BigDecimal precipitacion,
            @JsonProperty("precipitation_probability") BigDecimal probabilidadLluvia,
            @JsonProperty("wind_speed_10m") BigDecimal viento
    ) {
    }
}
