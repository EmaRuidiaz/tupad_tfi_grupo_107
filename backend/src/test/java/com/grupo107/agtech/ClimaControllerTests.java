package com.grupo107.agtech;

import com.grupo107.agtech.service.clima.ClimaClient;
import com.grupo107.agtech.service.clima.LecturaClima;
import com.grupo107.agtech.service.clima.OpenMeteoApi;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** La API externa se reemplaza por un mock: los tests no dependen de la red. */
@SpringBootTest(properties = "app.clima.espera-reintento=1ms")
@AutoConfigureMockMvc
@Transactional
class ClimaControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClimaClient climaClient;

    @MockitoBean
    private OpenMeteoApi openMeteoApi;

    @BeforeEach
    void limpiarEstadoDelCliente() {
        climaClient.reiniciar();
    }

    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private static LecturaClima lectura() {
        return new LecturaClima(
                new BigDecimal("24.5"),
                new BigDecimal("12.3"),
                new BigDecimal("15"),
                new BigDecimal("55"),
                new BigDecimal("0.0"),
                LocalDateTime.of(2026, 10, 31, 16, 30));
    }

    @Test
    void cualquierRolConsultaElClimaActualDeUnLote() throws Exception {
        when(openMeteoApi.consultar(any(), any())).thenReturn(lectura());

        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/lotes/1/clima").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loteId").value(1))
                    .andExpect(jsonPath("$.loteNombre").value("Lote Norte - La Escondida"))
                    .andExpect(jsonPath("$.latitud").value(-33.892))
                    .andExpect(jsonPath("$.longitud").value(-60.573))
                    .andExpect(jsonPath("$.temperaturaC").value(24.5))
                    .andExpect(jsonPath("$.vientoVelocidadKmh").value(12.3))
                    .andExpect(jsonPath("$.probabilidadLluviaPct").value(15))
                    .andExpect(jsonPath("$.humedadRelativaPct").value(55))
                    .andExpect(jsonPath("$.precipitacionMm").value(0.0))
                    .andExpect(jsonPath("$.fechaLectura").value("2026-10-31T16:30:00"))
                    .andExpect(jsonPath("$.origen").value("API"))
                    .andExpect(jsonPath("$.obtenidaEn").isNotEmpty());
        }

        verify(openMeteoApi, times(3)).consultar(
                eq(new BigDecimal("-33.892000")), eq(new BigDecimal("-60.573000")));
    }

    @Test
    void siLaApiFallaUsaLaLecturaEnCacheDelLote() throws Exception {
        String token = bearer("agronomo1");
        when(openMeteoApi.consultar(any(), any()))
                .thenReturn(lectura())
                .thenThrow(new ResourceAccessException("timeout"));

        String primera = mockMvc.perform(get("/api/lotes/2/clima").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("API"))
                .andReturn().getResponse().getContentAsString();
        String obtenidaEn = JsonPath.read(primera, "$.obtenidaEn");

        mockMvc.perform(get("/api/lotes/2/clima").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("CACHE"))
                .andExpect(jsonPath("$.obtenidaEn").value(obtenidaEn))
                .andExpect(jsonPath("$.vientoVelocidadKmh").value(12.3));
    }

    @Test
    void sinApiNiCacheDevuelve503SinAfectarAlRestoDelSistema() throws Exception {
        String token = bearer("operario1");
        when(openMeteoApi.consultar(any(), any())).thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(get("/api/lotes/3/clima").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(containsString("no disponible")));

        // 1 intento + 2 reintentos (doc 07, sección 7.3).
        verify(openMeteoApi, times(3)).consultar(any(), any());

        // RNF-03: la caída del clima no afecta a los demás módulos.
        mockMvc.perform(get("/api/lotes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void loteInexistenteDevuelve404SinConsultarLaApi() throws Exception {
        mockMvc.perform(get("/api/lotes/999/clima").header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Lote no encontrado")));

        verifyNoInteractions(openMeteoApi);
    }

    @Test
    void sinTokenNoSePuedeConsultarElClima() throws Exception {
        mockMvc.perform(get("/api/lotes/1/clima"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(openMeteoApi);
    }
}
