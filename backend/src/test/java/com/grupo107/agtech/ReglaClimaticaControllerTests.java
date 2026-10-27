package com.grupo107.agtech;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReglaClimaticaControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String reglaJson(String tipoLabor, String vientoMin, String vientoMax,
                             String tempMin, String tempMax, String probLluviaMax,
                             String humedadMin, String mensaje) {
        return """
                {
                  "tipoLabor": "%s",
                  "vientoMinKmh": %s,
                  "vientoMaxKmh": %s,
                  "tempMinC": %s,
                  "tempMaxC": %s,
                  "probLluviaMaxPct": %s,
                  "humedadMinPct": %s,
                  "mensajeAlerta": "%s"
                }
                """.formatted(tipoLabor, vientoMin, vientoMax, tempMin, tempMax, probLluviaMax, humedadMin, mensaje);
    }

    @Test
    void cualquierRolAutenticadoPuedeListarReglasClimaticasYFiltrarPorTipoLabor() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/reglas-climaticas").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].tipoLabor").value("PULVERIZACION"))
                    .andExpect(jsonPath("$[0].vientoMinKmh").value(2.0))
                    .andExpect(jsonPath("$[0].vientoMaxKmh").value(15.0))
                    .andExpect(jsonPath("$[0].tempMinC").value(10.0))
                    .andExpect(jsonPath("$[0].tempMaxC").value(32.0))
                    .andExpect(jsonPath("$[0].probLluviaMaxPct").value(20.0))
                    .andExpect(jsonPath("$[0].humedadMinPct").value(40.0));
        }

        mockMvc.perform(get("/api/reglas-climaticas")
                        .param("tipoLabor", "SIEMBRA")
                        .header("Authorization", bearer("agronomo1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipoLabor").value("SIEMBRA"))
                .andExpect(jsonPath("$[0].vientoMaxKmh").value(28.0));
    }

    @Test
    void agronomoYOperarioReciben403AlIntentarCrearEditarOEliminarReglas() throws Exception {
        String payload = reglaJson("PULVERIZACION", "2", "14", "10", "30", "15", "45", "Regla modificada");

        for (String user : new String[]{"agronomo1", "operario1"}) {
            String token = bearer(user);

            mockMvc.perform(post("/api/reglas-climaticas").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/reglas-climaticas/1").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/reglas-climaticas/1").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminPuedeEditarEliminarYVolverACrearUnaReglaClimatica() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(put("/api/reglas-climaticas/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("PULVERIZACION", "3.00", "14.50", "12.00", "30.00", "15.00", "45.00",
                                "Riesgo alto de deriva por viento o baja humedad.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vientoMinKmh").value(3.0))
                .andExpect(jsonPath("$.vientoMaxKmh").value(14.5))
                .andExpect(jsonPath("$.humedadMinPct").value(45.0))
                .andExpect(jsonPath("$.mensajeAlerta").value("Riesgo alto de deriva por viento o baja humedad."));

        mockMvc.perform(delete("/api/reglas-climaticas/4").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reglas-climaticas/4").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/reglas-climaticas")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("FERTILIZACION", "0.00", "20.00", "6.00", "34.00", "35.00", "35.00",
                                "Viento fuerte impide distribución uniforme de fertilizante.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tipoLabor").value("FERTILIZACION"))
                .andExpect(jsonPath("$.vientoMaxKmh").value(20.0));
    }

    @Test
    void rechazaTipoLaborDuplicadoOTipoLabranzaCon400() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(post("/api/reglas-climaticas")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("PULVERIZACION", "2", "15", "10", "30", "20", "40", "Duplicada")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Ya existe una regla climática")));

        mockMvc.perform(put("/api/reglas-climaticas/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("SIEMBRA", "2", "15", "10", "30", "20", "40", "Colisión con siembra")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Ya existe otra regla climática")));

        mockMvc.perform(post("/api/reglas-climaticas")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("LABRANZA", "0", "30", "5", "35", "50", "20", "Labranza no lleva regla")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("LABRANZA")));
    }

    @Test
    void validaRangosCruzadosDeVientoTemperaturaYPorcentajes() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(put("/api/reglas-climaticas/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("PULVERIZACION", "20.00", "15.00", "10.00", "30.00", "20.00", "40.00",
                                "Viento mínimo mayor al máximo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("velocidad mínima de viento")));

        mockMvc.perform(put("/api/reglas-climaticas/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("PULVERIZACION", "2.00", "15.00", "30.00", "30.00", "20.00", "40.00",
                                "Temp mínima igual a máxima")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("temperatura mínima")));

        mockMvc.perform(put("/api/reglas-climaticas/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("PULVERIZACION", "2.00", "15.00", "10.00", "30.00", "120.00", "-5.00",
                                "Porcentajes fuera de rango")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("probabilidad máxima de lluvia debe estar entre 0 y 100"),
                        containsString("humedad relativa mínima debe estar entre 0 y 100"))));

        mockMvc.perform(put("/api/reglas-climaticas/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reglaJson("PULVERIZACION", "2.123", "15.00", "10.00", "30.00", "20.00", "40.00",
                                "Precisión inválida")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("3 enteros y 2 decimales")));
    }
}
