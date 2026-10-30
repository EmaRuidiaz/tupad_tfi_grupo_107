package com.grupo107.agtech;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LaborControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void cualquierRolAutenticadoPuedeListarLaboresYFiltrarPorLoteEstadoYTipo() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/labores").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].id").value(3))
                    .andExpect(jsonPath("$[0].estado").value("PLANIFICADA"))
                    .andExpect(jsonPath("$[0].tipoLabor").value("PULVERIZACION"))
                    .andExpect(jsonPath("$[0].insumos", hasSize(1)))
                    .andExpect(jsonPath("$[0].insumos[0].estadoReserva").value("RESERVADO"))
                    .andExpect(jsonPath("$[0].insumos[0].cantidadUtilizada").value(170.0));
        }

        String tokenAgro = bearer("agronomo1");
        mockMvc.perform(get("/api/labores")
                        .param("estado", "COMPLETADA")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/labores")
                        .param("loteId", "2")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].loteNombre").value("Lote Sur - El Bajío"));

        mockMvc.perform(get("/api/labores")
                        .param("tipoLabor", "SIEMBRA")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void datosSemillaCumplenInvarianteStockReservadoIgualASumaDeReservasActivas() {
        Integer inconsistentes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM insumo i
                WHERE i.stock_reservado <> COALESCE(
                    (SELECT SUM(li.cantidad_utilizada) FROM labor_insumo li
                     WHERE li.insumo_id = i.id AND li.estado_reserva = 'RESERVADO'), 0)
                """, Integer.class);
        assertEquals(0, inconsistentes,
                "Todo insumo debe cumplir stock_reservado = SUM(labor_insumo.cantidad_utilizada) con estado_reserva = 'RESERVADO'");
    }

    @Test
    void agronomoPuedeProgramarLaborYSeReservaElStockSinAlterarElStockFisico() throws Exception {
        String tokenAgro = bearer("agronomo1");

        String response = mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "maquinariaId": 1,
                                  "tipoLabor": "SIEMBRA",
                                  "fechaPlanificada": "2026-10-30T08:30:00",
                                  "observaciones": "Siembra de soja de primera campaña",
                                  "insumos": [
                                    { "insumoId": 1, "cantidadUtilizada": 100.00 },
                                    { "insumoId": 4, "cantidadUtilizada": 150.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.estado").value("PLANIFICADA"))
                .andExpect(jsonPath("$.tipoLabor").value("SIEMBRA"))
                .andExpect(jsonPath("$.loteId").value(2))
                .andExpect(jsonPath("$.maquinariaId").value(1))
                .andExpect(jsonPath("$.usuarioUsername").value("agronomo1"))
                .andExpect(jsonPath("$.insumos", hasSize(2)))
                .andExpect(jsonPath("$.insumos[0].insumoId").value(1))
                .andExpect(jsonPath("$.insumos[0].cantidadUtilizada").value(100.0))
                .andExpect(jsonPath("$.insumos[0].estadoReserva").value("RESERVADO"))
                .andExpect(jsonPath("$.insumos[1].insumoId").value(4))
                .andExpect(jsonPath("$.insumos[1].cantidadUtilizada").value(150.0))
                .andExpect(jsonPath("$.insumos[1].estadoReserva").value("RESERVADO"))
                .andReturn().getResponse().getContentAsString();

        Integer idLabor = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/insumos/1").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(450.0))
                .andExpect(jsonPath("$.stockReservado").value(100.0))
                .andExpect(jsonPath("$.stockDisponible").value(350.0));

        mockMvc.perform(get("/api/insumos/4").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(5000.0))
                .andExpect(jsonPath("$.stockReservado").value(150.0))
                .andExpect(jsonPath("$.stockDisponible").value(4850.0));

        mockMvc.perform(get("/api/labores/" + idLabor).header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idLabor))
                .andExpect(jsonPath("$.costoInsumosEstimado").value(5665.0));
    }

    @Test
    void editarLaborPendienteReemplazaReservasAtomicamenteYBloqueaEdicionDeLaborCompletada() throws Exception {
        String tokenAgro = bearer("agronomo1");

        // Labor 3 tiene reservado insumo 2 (170 L). Editamos para usar 200 L de insumo 2 y 100 L de insumo 3.
        mockMvc.perform(put("/api/labores/3")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "maquinariaId": 2,
                                  "tipoLabor": "PULVERIZACION",
                                  "fechaPlanificada": "2026-09-19T08:00:00",
                                  "observaciones": "Dosis ajustada con coadyuvante",
                                  "insumos": [
                                    { "insumoId": 2, "cantidadUtilizada": 200.00 },
                                    { "insumoId": 3, "cantidadUtilizada": 100.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.insumos", hasSize(2)))
                .andExpect(jsonPath("$.insumos[0].cantidadUtilizada").value(200.0))
                .andExpect(jsonPath("$.insumos[1].cantidadUtilizada").value(100.0));

        // Insumo 2 pasa de 170 reservado a 200 reservado (disponible = 1000)
        mockMvc.perform(get("/api/insumos/2").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockReservado").value(200.0))
                .andExpect(jsonPath("$.stockDisponible").value(1000.0));

        // Insumo 3 pasa de 0 reservado a 100 reservado
        mockMvc.perform(get("/api/insumos/3").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockReservado").value(100.0));

        // No se puede editar una labor COMPLETADA
        mockMvc.perform(put("/api/labores/1")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "tipoLabor": "LABRANZA",
                                  "fechaPlanificada": "2026-10-01T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void operarioRecibe403AlIntentarProgramarEditarReprogramarOCancelarLabores() throws Exception {
        String tokenOperario = bearer("operario1");
        String body = """
                {
                  "loteId": 2,
                  "tipoLabor": "LABRANZA",
                  "fechaPlanificada": "2026-10-30T09:00:00",
                  "insumos": []
                }
                """;

        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/labores/3")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/labores/3/reprogramar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaPlanificada\":\"2026-10-31T10:00:00\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/labores/3/cancelar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaProgramacionSiStockDisponibleEsInsuficienteEInformaCantidadFaltanteSinReservarNada() throws Exception {
        String tokenAgro = bearer("agronomo1");

        // Insumo 2 (Glifosato) tiene stockActual=1200 y stockReservado=170 -> disponible=1030.
        // Solicitamos 1100 (faltan 70 L) junto con Insumo 1 para verificar atomicidad.
        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 1,
                                  "maquinariaId": 2,
                                  "tipoLabor": "PULVERIZACION",
                                  "fechaPlanificada": "2026-11-01T08:00:00",
                                  "insumos": [
                                    { "insumoId": 1, "cantidadUtilizada": 50.00 },
                                    { "insumoId": 2, "cantidadUtilizada": 1100.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("Stock disponible insuficiente"),
                        containsString("Glifosato Roundup Control 48%"),
                        containsString("faltan 70.00 LITROS")
                )));

        mockMvc.perform(get("/api/insumos/1").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockReservado").value(0.0));
    }

    @Test
    void rechazaAsignarMaquinariaNoOperativaOConConflictoHorario() throws Exception {
        String tokenAgro = bearer("agronomo1");

        // Labor 3 tiene maquinaria 2 el 2026-09-18 10:00:00. Intentar usar maquinaria 2 a las 11:00 da 409.
        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 1,
                                  "maquinariaId": 2,
                                  "tipoLabor": "PULVERIZACION",
                                  "fechaPlanificada": "2026-09-18T11:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ya está asignada a la orden pendiente #3")));

        // Pasar maquinaria 1 a EN_MANTENIMIENTO impide asignarla
        mockMvc.perform(patch("/api/maquinarias/1/estado")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_MANTENIMIENTO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "maquinariaId": 1,
                                  "tipoLabor": "LABRANZA",
                                  "fechaPlanificada": "2026-11-02T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("EN_MANTENIMIENTO")));
    }

    @Test
    void validaCoherenciaAgronomicaEntreEstadoDelLoteYTipoDeLabor() throws Exception {
        String tokenAgro = bearer("agronomo1");

        // Lote 1 está SEMBRADO: no admite SIEMBRA, LABRANZA ni COSECHA
        for (String tipoInvalido : new String[]{"SIEMBRA", "LABRANZA", "COSECHA"}) {
            mockMvc.perform(post("/api/labores")
                            .header("Authorization", tokenAgro)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "loteId": 1,
                                      "tipoLabor": "%s",
                                      "fechaPlanificada": "2026-11-03T08:00:00",
                                      "insumos": []
                                    }
                                    """.formatted(tipoInvalido)))
                    .andExpect(status().isConflict());
        }

        // Lote 3 está LISTO_COSECHA: admite COSECHA
        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 3,
                                  "maquinariaId": 3,
                                  "tipoLabor": "COSECHA",
                                  "fechaPlanificada": "2026-11-03T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoLabor").value("COSECHA"));

        // Lote en DESCANSO solo permite LABRANZA
        jdbcTemplate.update("UPDATE lote SET estado = 'DESCANSO' WHERE id = 2");

        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "tipoLabor": "PULVERIZACION",
                                  "fechaPlanificada": "2026-11-03T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("DESCANSO")));

        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "tipoLabor": "LABRANZA",
                                  "fechaPlanificada": "2026-11-03T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoLabor").value("LABRANZA"))
                .andExpect(jsonPath("$.estado").value("PLANIFICADA"));
    }

    @Test
    void rechazaInsumosDuplicadosEnLaMismaOrdenDeTrabajo() throws Exception {
        String tokenAgro = bearer("agronomo1");

        mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "tipoLabor": "FERTILIZACION",
                                  "fechaPlanificada": "2026-11-04T08:00:00",
                                  "insumos": [
                                    { "insumoId": 3, "cantidadUtilizada": 100.00 },
                                    { "insumoId": 3, "cantidadUtilizada": 50.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("repetir el mismo insumo")));
    }

    @Test
    void reprogramarLaborPendienteActualizaFechaYConservaReservasPeroBloqueaEnCompletada() throws Exception {
        String tokenAgro = bearer("agronomo1");

        mockMvc.perform(patch("/api/labores/3/reprogramar")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fechaPlanificada": "2026-11-10T09:30:00",
                                  "observaciones": "Reprogramada por pronóstico de lluvias"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.estado").value("PLANIFICADA"))
                .andExpect(jsonPath("$.fechaPlanificada").value("2026-11-10T09:30:00"))
                .andExpect(jsonPath("$.observacionesClimaticas").value("Reprogramada por pronóstico de lluvias"))
                .andExpect(jsonPath("$.insumos[0].estadoReserva").value("RESERVADO"));

        mockMvc.perform(get("/api/insumos/2").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockReservado").value(170.0));

        mockMvc.perform(patch("/api/labores/1/reprogramar")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaPlanificada\":\"2026-11-12T08:00:00\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelarLaborPlanificadaLiberaReservasDeStockYBloqueaCancelacionDeLaborCompletada() throws Exception {
        String tokenAgro = bearer("agronomo1");

        mockMvc.perform(post("/api/labores/3/cancelar")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.insumos[0].estadoReserva").value("LIBERADO"));

        mockMvc.perform(get("/api/insumos/2").header("Authorization", tokenAgro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(1200.0))
                .andExpect(jsonPath("$.stockReservado").value(0.0))
                .andExpect(jsonPath("$.stockDisponible").value(1200.0));

        mockMvc.perform(post("/api/labores/3/cancelar")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/labores/1/cancelar")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isConflict());
    }
}
