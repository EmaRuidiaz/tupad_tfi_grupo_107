package com.grupo107.agtech;

import com.grupo107.agtech.service.clima.ClimaClient;
import com.grupo107.agtech.service.clima.LecturaClima;
import com.grupo107.agtech.service.clima.OpenMeteoApi;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-10: la labor 3 de la semilla es una PULVERIZACION PLANIFICADA en el Lote 2, cuya regla exige
 * viento entre 2 y 15 km/h, temperatura entre 10 y 32 °C, lluvia hasta 20 % y humedad desde 40 %.
 * La API externa se reemplaza por un mock: los tests no dependen de la red.
 */
@SpringBootTest(properties = "app.clima.espera-reintento=1ms")
@AutoConfigureMockMvc
@Transactional
class AutorizacionClimaticaControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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

    private static LecturaClima lectura(String vientoKmh) {
        return new LecturaClima(
                new BigDecimal("24.5"),
                new BigDecimal(vientoKmh),
                new BigDecimal("10"),
                new BigDecimal("55"),
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 11, 6, 9, 30));
    }

    private String estadoLabor3() {
        return jdbcTemplate.queryForObject("SELECT estado FROM labor_agricola WHERE id = 3", String.class);
    }

    private String estadoReservaLabor3() {
        return jdbcTemplate.queryForObject("SELECT estado_reserva FROM labor_insumo WHERE labor_id = 3", String.class);
    }

    @Test
    void climaOptimoApruebaLaLaborGuardaLasVariablesYPermiteIniciarla() throws Exception {
        String token = bearer("operario1");
        when(openMeteoApi.consultar(any(), any())).thenReturn(lectura("8.4"));

        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.autorizacionManual").value(false))
                .andExpect(jsonPath("$.estadoResultante").value("APROBADA_CLIMA"))
                .andExpect(jsonPath("$.incumplimientos", hasSize(0)))
                .andExpect(jsonPath("$.mensajeAlerta").doesNotExist())
                .andExpect(jsonPath("$.vientoVelocidadKmh").value(8.4))
                .andExpect(jsonPath("$.origen").value("API"))
                .andExpect(jsonPath("$.labor.id").value(3))
                .andExpect(jsonPath("$.labor.estado").value("APROBADA_CLIMA"))
                .andExpect(jsonPath("$.labor.temperaturaRegistrada").value(24.5))
                .andExpect(jsonPath("$.labor.vientoVelocidadKmh").value(8.4))
                .andExpect(jsonPath("$.labor.probabilidadLluviaPct").value(10))
                .andExpect(jsonPath("$.labor.humedadRegistradaPct").value(55));

        // Se consultan las coordenadas del lote de la labor (Lote 2).
        verify(openMeteoApi).consultar(eq(new BigDecimal("-33.915000")), eq(new BigDecimal("-60.591000")));

        // Con la aprobación, la HU-11 permite iniciar la labor.
        mockMvc.perform(post("/api/labores/3/iniciar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"));
    }

    @Test
    void climaAdversoBloqueaLaLaborConElMotivoSinConsumirStockYSePuedeRevalidar() throws Exception {
        String token = bearer("agronomo1");
        when(openMeteoApi.consultar(any(), any()))
                .thenReturn(lectura("25"))
                .thenReturn(lectura("9"));

        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(false))
                .andExpect(jsonPath("$.estadoResultante").value("BLOQUEADA_CLIMA"))
                .andExpect(jsonPath("$.incumplimientos", hasSize(1)))
                .andExpect(jsonPath("$.incumplimientos[0]").value(containsString("Viento (25 km/h) supera el máximo")))
                .andExpect(jsonPath("$.mensajeAlerta").value(containsString("deriva")))
                .andExpect(jsonPath("$.labor.estado").value("BLOQUEADA_CLIMA"))
                .andExpect(jsonPath("$.labor.vientoVelocidadKmh").value(25));

        // RN-01: bloqueada no se inicia y las reservas se mantienen.
        mockMvc.perform(post("/api/labores/3/iniciar").header("Authorization", token))
                .andExpect(status().isConflict());
        assertEquals("RESERVADO", estadoReservaLabor3());

        // Revalidación desde BLOQUEADA_CLIMA cuando mejora el clima.
        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.labor.estado").value("APROBADA_CLIMA"))
                .andExpect(jsonPath("$.labor.vientoVelocidadKmh").value(9));

        // Una labor ya aprobada no se vuelve a validar.
        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ya cuenta con aprobación")));
        verify(openMeteoApi, times(2)).consultar(any(), any());
    }

    @Test
    void sinApiNiCacheDevuelve503YLaLaborNoCambia() throws Exception {
        when(openMeteoApi.consultar(any(), any())).thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", bearer("operario1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("no disponible")));

        assertEquals("PLANIFICADA", estadoLabor3());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM labor_agricola WHERE id = 3 AND viento_velocidad_kmh IS NOT NULL", Integer.class));
    }

    @Test
    void rechazaValidarLaboresQueNoCorrespondenSinConsultarLaApi() throws Exception {
        String token = bearer("agronomo1");

        mockMvc.perform(post("/api/labores/999/validar-clima").header("Authorization", token))
                .andExpect(status().isNotFound());

        // Labor 1: SIEMBRA COMPLETADA.
        mockMvc.perform(post("/api/labores/1/validar-clima").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("COMPLETADA")));

        // Tipo de labor sin regla configurada.
        jdbcTemplate.update("DELETE FROM regla_climatica WHERE tipo_labor = 'PULVERIZACION'");
        entityManager.clear();
        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("No hay una regla climática")));

        // LABRANZA no requiere validación climática (RN-01).
        jdbcTemplate.update("UPDATE labor_agricola SET tipo_labor = 'LABRANZA' WHERE id = 3");
        entityManager.clear();
        mockMvc.perform(post("/api/labores/3/validar-clima").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("no requieren validación climática")));

        mockMvc.perform(post("/api/labores/3/validar-clima"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(openMeteoApi);
    }

    @Test
    void autorizacionManualSoloProcedeAnteContingenciaYLaHaceAgronomoOAdmin() throws Exception {
        String justificacion = "{\"justificacion\":\"Viento calmo medido en el lote con anemómetro\"}";
        when(openMeteoApi.consultar(any(), any())).thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(post("/api/labores/3/autorizacion-manual")
                        .header("Authorization", bearer("operario1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(justificacion))
                .andExpect(status().isForbidden());

        String tokenAgro = bearer("agronomo1");
        mockMvc.perform(post("/api/labores/3/autorizacion-manual")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificacion\":\"corta\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/labores/3/autorizacion-manual")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(justificacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.autorizacionManual").value(true))
                .andExpect(jsonPath("$.labor.estado").value("APROBADA_CLIMA"))
                .andExpect(jsonPath("$.labor.vientoVelocidadKmh").doesNotExist())
                .andExpect(jsonPath("$.labor.observacionesClimaticas").value(allOf(
                        containsString("Autorización manual por contingencia climática (agronomo1"),
                        containsString("Viento calmo medido en el lote con anemómetro"))));

        mockMvc.perform(get("/api/labores/3").header("Authorization", tokenAgro))
                .andExpect(jsonPath("$.estado").value("APROBADA_CLIMA"));
    }

    @Test
    void autorizacionManualSeRechazaSiElServicioClimaticoResponde() throws Exception {
        when(openMeteoApi.consultar(any(), any())).thenReturn(lectura("25"));

        mockMvc.perform(post("/api/labores/3/autorizacion-manual")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificacion\":\"Quiero saltear la validación del clima\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("está disponible")));

        assertEquals("PLANIFICADA", estadoLabor3());
        mockMvc.perform(get("/api/labores/3").header("Authorization", bearer("admin")))
                .andExpect(jsonPath("$.observacionesClimaticas").value(not(containsString("Autorización manual"))));
    }
}
