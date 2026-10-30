package com.grupo107.agtech;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MantenimientoControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String payload(String fecha, double lectura, double proximo) {
        return """
                {
                  "fechaService": "%s",
                  "horasMaquinaEnService": %s,
                  "tipoMantenimiento": "PREVENTIVO_PROGRAMADO",
                  "costo": 380.50,
                  "proximoServiceHoras": %s,
                  "descripcion": "Cambio de aceite y filtros"
                }
                """.formatted(fecha, lectura, proximo);
    }

    private String hoy() {
        return LocalDate.now().toString();
    }

    private void forzarEstado(long maquinariaId, String estado) {
        jdbcTemplate.update("UPDATE maquinaria SET estado = ? WHERE id = ?", estado, maquinariaId);
        entityManager.clear();
    }

    @Test
    void cualquierRolPuedeConsultarElHistorialDeUnaMaquinaria() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/maquinarias/1/mantenimientos").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].maquinariaId").value(1))
                    .andExpect(jsonPath("$[0].tipoMantenimiento").value("PREVENTIVO_PROGRAMADO"))
                    .andExpect(jsonPath("$[0].horasMaquinaEnService").value(750.0))
                    .andExpect(jsonPath("$[0].proximoServiceHoras").value(1000.0))
                    .andExpect(jsonPath("$[0].usuarioUsername").value("operario1"));
        }

        mockMvc.perform(get("/api/maquinarias/3/mantenimientos").header("Authorization", bearer("operario1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void registrarServiceActualizaElProximoServiceDeLaMaquinaria() throws Exception {
        String token = bearer("operario1");

        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 990.00, 1250.00)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.maquinariaId").value(1))
                .andExpect(jsonPath("$.proximoServiceHoras").value(1250.0))
                .andExpect(jsonPath("$.costo").value(380.5))
                .andExpect(jsonPath("$.usuarioUsername").value("operario1"));

        mockMvc.perform(get("/api/maquinarias/1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horasParaProximoService").value(1250.0))
                .andExpect(jsonPath("$.horasUsoActuales").value(990.0))
                .andExpect(jsonPath("$.horasRestantesService").value(260.0))
                .andExpect(jsonPath("$.mantenimientoVencido").value(false))
                .andExpect(jsonPath("$.estado").value("OPERATIVO"));

        mockMvc.perform(get("/api/maquinarias/1/mantenimientos").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].horasMaquinaEnService").value(990.0));
    }

    @Test
    void registrarServiceDejaOperativaUnaMaquinariaEnMantenimiento() throws Exception {
        String token = bearer("agronomo1");

        mockMvc.perform(patch("/api/maquinarias/3/estado")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_MANTENIMIENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_MANTENIMIENTO"));

        mockMvc.perform(post("/api/maquinarias/3/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 620.00, 900.00)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/maquinarias/3").header("Authorization", token))
                .andExpect(jsonPath("$.estado").value("OPERATIVO"))
                .andExpect(jsonPath("$.horasParaProximoService").value(900.0));
    }

    @Test
    void noSePuedeRegistrarServiceEnLaborNiFueraDeServicio() throws Exception {
        String token = bearer("admin");

        forzarEstado(3, "EN_LABOR");
        mockMvc.perform(post("/api/maquinarias/3/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 620.00, 900.00)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("labor en ejecución")));

        forzarEstado(3, "FUERA_DE_SERVICIO");
        mockMvc.perform(post("/api/maquinarias/3/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 620.00, 900.00)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("FUERA_DE_SERVICIO")));

        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registro_mantenimiento WHERE maquinaria_id = 3", Long.class));
    }

    @Test
    void validaConsistenciaDelHorometroYDelProximoService() throws Exception {
        String token = bearer("operario1");

        // Lectura menor al último service registrado (750 hs).
        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 700.00, 1200.00)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("último service")));

        // Fecha anterior al último service registrado (2026-03-10).
        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("2026-01-15", 990.00, 1200.00)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("fecha del service")));

        // Próximo service menor o igual a la lectura.
        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 990.00, 990.00)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("lectura del horómetro")));

        // Próximo service por debajo del horómetro actual (985 hs): la máquina quedaría vencida.
        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 900.00, 950.00)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("horómetro actual")));

        mockMvc.perform(get("/api/maquinarias/1").header("Authorization", token))
                .andExpect(jsonPath("$.horasParaProximoService").value(1000.0));
    }

    @Test
    void rechazaDatosInvalidosYMaquinariaInexistente() throws Exception {
        String token = bearer("operario1");

        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaEnService\": 990}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/maquinarias/1/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(LocalDate.now().plusDays(1).toString(), 990.00, 1250.00)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("futura")));

        mockMvc.perform(get("/api/maquinarias/999/mantenimientos").header("Authorization", token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/maquinarias/999/mantenimientos")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(hoy(), 990.00, 1250.00)))
                .andExpect(status().isNotFound());
    }
}
