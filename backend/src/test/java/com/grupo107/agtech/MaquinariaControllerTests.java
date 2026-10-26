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
class MaquinariaControllerTests {

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

    @Test
    void cualquierRolAutenticadoPuedeListarMaquinarias() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/maquinarias").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].nombre").value("Tractor John Deere 6110M"))
                    .andExpect(jsonPath("$[0].matriculaOSerie").value("JD-6110M-2022-01"))
                    .andExpect(jsonPath("$[0].horasUsoActuales").value(985.0))
                    .andExpect(jsonPath("$[0].horasParaProximoService").value(1000.0))
                    .andExpect(jsonPath("$[0].horasRestantesService").value(15.0))
                    .andExpect(jsonPath("$[0].estado").value("OPERATIVO"))
                    .andExpect(jsonPath("$[0].mantenimientoVencido").value(false));
        }
    }

    @Test
    void crearMaquinariaQuedaEnEstadoOperativoYRechazaMatriculaDuplicada() throws Exception {
        String tokenAdmin = bearer("admin");
        String payload = """
                {
                  "nombre": "Sembradora Agrometal TX Mega",
                  "tipo": "SEMBRADORA",
                  "matriculaOSerie": "AGRO-TX-2025",
                  "horasUsoActuales": 120.00,
                  "horasParaProximoService": 500.00,
                  "consumoLitrosHora": 0.00,
                  "costoOperativoHora": 35.00
                }
                """;

        mockMvc.perform(post("/api/maquinarias")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Sembradora Agrometal TX Mega"))
                .andExpect(jsonPath("$.estado").value("OPERATIVO"))
                .andExpect(jsonPath("$.horasRestantesService").value(380.0))
                .andExpect(jsonPath("$.mantenimientoVencido").value(false));

        mockMvc.perform(post("/api/maquinarias")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("matrícula o serie")));
    }

    @Test
    void agronomoYOperarioReciben403AlIntentarCrearEditarOEliminarMaquinaria() throws Exception {
        String payload = """
                {
                  "nombre": "Tractor Prueba",
                  "tipo": "TRACTOR",
                  "matriculaOSerie": "TR-999",
                  "horasUsoActuales": 0,
                  "horasParaProximoService": 250,
                  "consumoLitrosHora": 15,
                  "costoOperativoHora": 40
                }
                """;

        for (String user : new String[]{"agronomo1", "operario1"}) {
            String token = bearer(user);
            mockMvc.perform(post("/api/maquinarias").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/maquinarias/1").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/maquinarias/1").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void actualizarMaquinariaRecalculaAlertaDeMantenimientoVencido() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(put("/api/maquinarias/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Tractor John Deere 6110M",
                                  "tipo": "TRACTOR",
                                  "matriculaOSerie": "JD-6110M-2022-01",
                                  "horasUsoActuales": 1010.00,
                                  "horasParaProximoService": 1000.00,
                                  "consumoLitrosHora": 16.50,
                                  "costoOperativoHora": 48.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horasUsoActuales").value(1010.0))
                .andExpect(jsonPath("$.horasRestantesService").value(-10.0))
                .andExpect(jsonPath("$.mantenimientoVencido").value(true));
    }

    @Test
    void transicionesDeEstadoRespetanReglasDeNegocioYPermisosPorRol() throws Exception {
        String tokenOperario = bearer("operario1");
        String tokenAdmin = bearer("admin");

        mockMvc.perform(patch("/api/maquinarias/3/estado")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"EN_MANTENIMIENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_MANTENIMIENTO"));

        mockMvc.perform(patch("/api/maquinarias/3/estado")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"FUERA_DE_SERVICIO\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/maquinarias/3/estado")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"FUERA_DE_SERVICIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FUERA_DE_SERVICIO"));

        mockMvc.perform(patch("/api/maquinarias/3/estado")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"OPERATIVO\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/maquinarias/3/estado")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"EN_LABOR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminarMaquinariaSinHistorialDevuelve204YConHistorialDevuelve409() throws Exception {
        String tokenAdmin = bearer("admin");

        String body = mockMvc.perform(post("/api/maquinarias")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Camioneta Apoyo Hilux",
                                  "tipo": "VEHICULO_APOYO",
                                  "matriculaOSerie": "AF-123-ZZ",
                                  "horasUsoActuales": 0,
                                  "horasParaProximoService": 500,
                                  "consumoLitrosHora": 8.5,
                                  "costoOperativoHora": 20.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer idTemporal = JsonPath.read(body, "$.id");

        mockMvc.perform(delete("/api/maquinarias/" + idTemporal).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/maquinarias/" + idTemporal).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/maquinarias/1").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void valoresNumericosFueraDeRangoOPrecisionEnMaquinariaDevuelven400() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(post("/api/maquinarias")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Tractor con precisión inválida",
                                  "tipo": "TRACTOR",
                                  "matriculaOSerie": "TR-PREC-01",
                                  "horasUsoActuales": 10.123,
                                  "horasParaProximoService": 250.00,
                                  "consumoLitrosHora": 15.00,
                                  "costoOperativoHora": 40.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("horas de uso")));

        mockMvc.perform(post("/api/maquinarias")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Tractor con service cero",
                                  "tipo": "TRACTOR",
                                  "matriculaOSerie": "TR-SERV-00",
                                  "horasUsoActuales": 0,
                                  "horasParaProximoService": 0,
                                  "consumoLitrosHora": 15.00,
                                  "costoOperativoHora": 40.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("próximo service")));
    }
}
