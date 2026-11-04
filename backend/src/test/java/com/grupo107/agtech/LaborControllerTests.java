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

    @Test
    void iniciarYCompletarLaborDeLabranzaCumpleTransicionesDescuentaStockSumaHorasYMantieneInvariantes() throws Exception {
        String tokenAgro = bearer("agronomo1");
        String tokenOperario = bearer("operario1");

        // 1. Agrónomo programa una labor de LABRANZA (no requiere regla climática) en Lote 2 con Maquinaria 1 (985 hs, $45/h)
        //    e Insumo 4 (Gasoil: 100 L a $1.10 = $110.00).
        String creada = mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "maquinariaId": 1,
                                  "tipoLabor": "LABRANZA",
                                  "fechaPlanificada": "2026-10-20T08:00:00",
                                  "insumos": [
                                    { "insumoId": 4, "cantidadUtilizada": 100.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PLANIFICADA"))
                .andExpect(jsonPath("$.requiereValidacionClimatica").value(false))
                .andReturn().getResponse().getContentAsString();
        Integer laborId = JsonPath.read(creada, "$.id");

        // No se puede completar directamente desde PLANIFICADA sin pasar por EN_EJECUCION (RN-08)
        mockMvc.perform(post("/api/labores/" + laborId + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 5.50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("EN_EJECUCION")));

        // 2. Operario inicia la labor: pasa de PLANIFICADA a EN_EJECUCION, consume stock y pone maquinaria EN_LABOR
        mockMvc.perform(post("/api/labores/" + laborId + "/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(laborId))
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.fechaEjecucion").isNotEmpty())
                .andExpect(jsonPath("$.costoInsumosConsumido").value(110.0))
                .andExpect(jsonPath("$.insumos[0].estadoReserva").value("CONSUMIDO"))
                .andExpect(jsonPath("$.insumos[0].costoSubtotal").value(110.0));

        // Stock de Insumo 4 descontado físicamente (5000 -> 4900) y reserva liberada
        mockMvc.perform(get("/api/insumos/4").header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(4900.0))
                .andExpect(jsonPath("$.stockReservado").value(0.0))
                .andExpect(jsonPath("$.stockDisponible").value(4900.0));

        // Movimiento EGRESO_LABOR registrado con el operario autenticado
        mockMvc.perform(get("/api/insumos/4/movimientos").header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("EGRESO_LABOR"))
                .andExpect(jsonPath("$[0].cantidad").value(-100.0))
                .andExpect(jsonPath("$[0].laborId").value(laborId))
                .andExpect(jsonPath("$[0].usuarioUsername").value("operario1"));

        // Maquinaria 1 en estado EN_LABOR
        mockMvc.perform(get("/api/maquinarias/1").header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_LABOR"));

        // 3. Operario completa la labor con 5.50 hs de máquina ($247.50) -> total = 110.00 + 247.50 = 357.50
        mockMvc.perform(post("/api/labores/" + laborId + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "horasMaquinaConsumidas": 5.50,
                                  "observaciones": "Labranza vertical completada sin novedades"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(laborId))
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.horasMaquinaConsumidas").value(5.5))
                .andExpect(jsonPath("$.costoInsumosConsumido").value(110.0))
                .andExpect(jsonPath("$.costoMaquinaria").value(247.5))
                .andExpect(jsonPath("$.costoTotalCalculado").value(357.5));

        // Maquinaria 1 suma 5.50 hs (985 -> 990.50) y vuelve a OPERATIVO
        mockMvc.perform(get("/api/maquinarias/1").header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horasUsoActuales").value(990.5))
                .andExpect(jsonPath("$.estado").value("OPERATIVO"));

        // 4. Verificación de invariantes globales de stock tras iniciar y completar la labor (doc 03 §3.15)
        Integer inconsistentesStockActual = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM insumo i
                WHERE i.stock_actual <> COALESCE(
                    (SELECT SUM(m.cantidad) FROM movimiento_stock m WHERE m.insumo_id = i.id), 0)
                """, Integer.class);
        assertEquals(0, inconsistentesStockActual,
                "Todo insumo debe mantener stock_actual = SUM(movimiento_stock.cantidad)");

        Integer inconsistentesReservado = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM insumo i
                WHERE i.stock_reservado <> COALESCE(
                    (SELECT SUM(li.cantidad_utilizada) FROM labor_insumo li
                     WHERE li.insumo_id = i.id AND li.estado_reserva = 'RESERVADO'), 0)
                """, Integer.class);
        assertEquals(0, inconsistentesReservado,
                "Todo insumo debe mantener stock_reservado = SUM(labor_insumo.cantidad_utilizada) en RESERVADO");
    }

    @Test
    void laborConReglaClimaticaExigeAprobadaClimaParaIniciarActualizaLoteSembradoYPreservaCostoHistorico() throws Exception {
        String tokenAgro = bearer("agronomo1");
        String tokenOperario = bearer("operario1");
        String tokenAdmin = bearer("admin");

        // 1. Agrónomo programa SIEMBRA en Lote 2 (EN_PREPARACION) con Maquinaria 1 (985 hs, service 1000 hs, $45/h)
        //    e Insumo 1 (100 bolsas a $55.00 = $5500.00).
        String creada = mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "maquinariaId": 1,
                                  "tipoLabor": "SIEMBRA",
                                  "fechaPlanificada": "2026-10-25T08:00:00",
                                  "insumos": [
                                    { "insumoId": 1, "cantidadUtilizada": 100.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer laborId = JsonPath.read(creada, "$.id");

        // RN-01: SIEMBRA tiene regla climática asociada, por lo que no puede iniciarse desde PLANIFICADA sin APROBADA_CLIMA
        mockMvc.perform(post("/api/labores/" + laborId + "/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("APROBADA_CLIMA")));

        // Simulamos aprobación climática previa (HU-10)
        jdbcTemplate.update("UPDATE labor_agricola SET estado = 'APROBADA_CLIMA' WHERE id = ?", laborId);
        entityManager.clear();

        // Tampoco se puede completar directamente desde APROBADA_CLIMA sin iniciar antes (RN-08)
        mockMvc.perform(post("/api/labores/" + laborId + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 20.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("EN_EJECUCION")));

        // 2. Operario inicia la ejecución de la labor desde APROBADA_CLIMA
        mockMvc.perform(post("/api/labores/" + laborId + "/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.fechaEjecucion").isNotEmpty())
                .andExpect(jsonPath("$.costoInsumosConsumido").value(5500.0))
                .andExpect(jsonPath("$.insumos[0].estadoReserva").value("CONSUMIDO"))
                .andExpect(jsonPath("$.insumos[0].costoSubtotal").value(5500.0));

        // Una labor EN_EJECUCION no puede cancelarse
        mockMvc.perform(post("/api/labores/" + laborId + "/cancelar")
                        .header("Authorization", tokenAgro))
                .andExpect(status().isConflict());

        // 3. Admin modifica el precio unitario del Insumo 1 de $55 a $90 para verificar RN-05 (costeo inmutable)
        mockMvc.perform(put("/api/insumos/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Semilla Soja Don Mario 46i20",
                                  "categoria": "SEMILLA",
                                  "unidadMedida": "BOLSAS",
                                  "stockMinimoAlerta": 100.00,
                                  "precioUnitario": 90.00
                                }
                                """))
                .andExpect(status().isOk());

        // 4. Operario completa la labor informando 20 hs de máquina ($900.00).
        //    El costo total debe ser 5500.00 (subtotal histórico) + 900.00 = 6400.00.
        mockMvc.perform(post("/api/labores/" + laborId + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "horasMaquinaConsumidas": 20.00,
                                  "observaciones": "Siembra finalizada sin inconvenientes"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.horasMaquinaConsumidas").value(20.0))
                .andExpect(jsonPath("$.costoInsumosConsumido").value(5500.0))
                .andExpect(jsonPath("$.costoMaquinaria").value(900.0))
                .andExpect(jsonPath("$.costoTotalCalculado").value(6400.0))
                .andExpect(jsonPath("$.loteEstado").value("SEMBRADO"));

        // Maquinaria 1 vuelve a OPERATIVO, suma 20 hs (985 -> 1005) y queda con mantenimiento vencido (>= 1000 hs)
        mockMvc.perform(get("/api/maquinarias/1").header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OPERATIVO"))
                .andExpect(jsonPath("$.horasUsoActuales").value(1005.0))
                .andExpect(jsonPath("$.mantenimientoVencido").value(true));

        // Lote 2 pasó automáticamente de EN_PREPARACION a SEMBRADO
        mockMvc.perform(get("/api/lotes/2").header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEMBRADO"));
    }

    @Test
    void rechazaInicioSiStockFisicoEsInsuficienteAlMomentoDeEjecutar() throws Exception {
        String tokenOperario = bearer("operario1");

        // Simulamos que la labor 3 fue aprobada climáticamente pero el stock físico cayó por debajo de lo requerido (CU-04 flujo 6a)
        jdbcTemplate.update("UPDATE labor_agricola SET estado = 'APROBADA_CLIMA' WHERE id = 3");
        jdbcTemplate.update("UPDATE insumo SET stock_reservado = 0, stock_actual = 100.00 WHERE id = 2");
        entityManager.clear();

        mockMvc.perform(post("/api/labores/3/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("Stock físico insuficiente"),
                        containsString("faltan 70.00 LITROS")
                )));
    }

    @Test
    void validacionesDeCierreRechazanFaltaDeTokenHorasInvalidasTopeDiarioYEstadosNoEjecutables() throws Exception {
        String tokenOperario = bearer("operario1");
        String tokenAgro = bearer("agronomo1");

        // 1. Peticiones sin token devuelven 401 Unauthorized (RF-01)
        mockMvc.perform(post("/api/labores/3/iniciar"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/labores/3/completar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 5.00}"))
                .andExpect(status().isUnauthorized());

        // 2. Programamos e iniciamos una labor con maquinaria para validar horas de cierre (0 hs y > 24 hs/día)
        String conMaq = mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "maquinariaId": 1,
                                  "tipoLabor": "LABRANZA",
                                  "fechaPlanificada": "2026-10-27T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer idConMaq = JsonPath.read(conMaq, "$.id");

        mockMvc.perform(post("/api/labores/" + idConMaq + "/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"));

        // Rechaza 0 hs en labor con maquinaria
        mockMvc.perform(post("/api/labores/" + idConMaq + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("horas reales de máquina")));

        // Rechaza horas que superan el máximo físico posible desde fechaEjecucion (24 hs por día calendario)
        mockMvc.perform(post("/api/labores/" + idConMaq + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 30.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("24 hs por día calendario")));

        // Completa válidamente con 6 hs
        mockMvc.perform(post("/api/labores/" + idConMaq + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 6.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));

        // 3. Labor manual (sin maquinaria): tras iniciarse, rechaza horas > 0 pero permite completar con 0 hs
        String manual = mockMvc.perform(post("/api/labores")
                        .header("Authorization", tokenAgro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loteId": 2,
                                  "tipoLabor": "LABRANZA",
                                  "fechaPlanificada": "2026-10-28T08:00:00",
                                  "insumos": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer idManual = JsonPath.read(manual, "$.id");

        mockMvc.perform(post("/api/labores/" + idManual + "/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"));

        mockMvc.perform(post("/api/labores/" + idManual + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 4.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("labor manual sin maquinaria")));

        mockMvc.perform(post("/api/labores/" + idManual + "/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.horasMaquinaConsumidas").value(0.0))
                .andExpect(jsonPath("$.costoTotalCalculado").value(0.0));

        // 4. No se puede volver a completar ni iniciar una labor ya COMPLETADA
        mockMvc.perform(post("/api/labores/1/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 5.00}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/labores/1/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isConflict());

        // 5. No se puede iniciar ni completar una labor en estado BLOQUEADA_CLIMA
        jdbcTemplate.update("UPDATE labor_agricola SET estado = 'BLOQUEADA_CLIMA' WHERE id = 3");
        entityManager.clear();

        mockMvc.perform(post("/api/labores/3/iniciar")
                        .header("Authorization", tokenOperario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("BLOQUEADA_CLIMA")));

        mockMvc.perform(post("/api/labores/3/completar")
                        .header("Authorization", tokenOperario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasMaquinaConsumidas\": 5.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("EN_EJECUCION")));
    }
}
