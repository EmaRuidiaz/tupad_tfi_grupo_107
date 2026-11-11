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

/**
 * HU-12 Registro de Cosecha (CU-05, RN-02, RN-04, RN-12).
 * Datos semilla: lote 1 SEMBRADO, lote 2 EN_PREPARACION, lote 3 LISTO_COSECHA (150 ha, sin labores ni cosechas).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CosechaControllerTests {

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

    private String payload(long loteId, long cultivoId, Long laborId, LocalDate fecha, String toneladas, String precio) {
        return """
                {
                  "loteId": %d,
                  "cultivoId": %d,
                  "laborId": %s,
                  "fechaCosecha": "%s",
                  "toneladasTotales": %s,
                  "precioVentaPorTon": %s
                }
                """.formatted(loteId, cultivoId, laborId == null ? "null" : laborId.toString(), fecha, toneladas, precio);
    }

    private LocalDate hoy() {
        return LocalDate.now();
    }

    private long insertarLabor(long loteId, String tipo, String estado, LocalDate fecha, String costo) {
        jdbcTemplate.update("""
                INSERT INTO labor_agricola (lote_id, usuario_id, tipo_labor, fecha_planificada, fecha_ejecucion,
                                            estado, horas_maquina_consumidas, costo_total_calculado)
                VALUES (?, 2, ?, ?, ?, ?, 0, ?)
                """, loteId, tipo, fecha.atTime(8, 0), fecha.atTime(17, 0), estado, new java.math.BigDecimal(costo));
        entityManager.clear();
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM labor_agricola", Long.class);
    }

    private void insertarCosechaAnterior(long loteId, LocalDate fecha) {
        jdbcTemplate.update("""
                INSERT INTO registro_cosecha (lote_id, cultivo_id, usuario_id, fecha_cosecha, toneladas_totales,
                                              rinde_ton_por_ha, precio_venta_por_ton, ingreso_bruto_total, margen_neto_calculado)
                VALUES (?, 4, 2, ?, 450.00, 3.00, 180000.00, 81000000.00, 80000000.00)
                """, loteId, fecha);
        entityManager.clear();
    }

    private void forzarEstadoLote(long loteId, String estado) {
        jdbcTemplate.update("UPDATE lote SET estado = ? WHERE id = ?", estado, loteId);
        entityManager.clear();
    }

    private int contarCosechas() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registro_cosecha", Integer.class);
    }

    // ---------- Catálogo de cultivos ----------

    @Test
    void cualquierRolConsultaElCatalogoDeCultivosOrdenadoPorNombre() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/cultivos").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].nombre").value("Maíz Tardío"))
                    .andExpect(jsonPath("$[0].especie").value("Maíz"))
                    .andExpect(jsonPath("$[3].nombre").value("Trigo"));
        }
    }

    // ---------- Registro y cálculos ----------

    @Test
    void registrarCosechaCalculaRindeIngresoYMargenYPasaElLoteACosechado() throws Exception {
        String token = bearer("agronomo1");

        mockMvc.perform(post("/api/cosechas")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy(), "1350.00", "210000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.loteId").value(3))
                .andExpect(jsonPath("$.loteNombre").value("Lote Este - Las Acacias"))
                .andExpect(jsonPath("$.cultivoNombre").value("Maíz Tardío"))
                .andExpect(jsonPath("$.laborId").doesNotExist())
                // rinde = 1350 t / 150 ha ; ingreso = 1350 t * 210000 $/t
                .andExpect(jsonPath("$.rindeTonPorHa").value(9.0))
                .andExpect(jsonPath("$.ingresoBrutoTotal").value(283500000.0))
                // Lote sin labores en el ciclo: sin costos -> margen = ingreso y ROI no calculable (RF-08, criterio 3).
                .andExpect(jsonPath("$.costosCicloImputados").value(0.0))
                .andExpect(jsonPath("$.margenNetoCalculado").value(283500000.0))
                .andExpect(jsonPath("$.roiPct").doesNotExist())
                .andExpect(jsonPath("$.usuarioUsername").value("agronomo1"));

        // RN-02: transición automática LISTO_COSECHA -> COSECHADO.
        mockMvc.perform(get("/api/lotes/3").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COSECHADO"));

        mockMvc.perform(get("/api/cosechas?loteId=3").header("Authorization", bearer("operario1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].toneladasTotales").value(1350.0));
    }

    @Test
    void elMargenNetoSoloDescuentaLasLaboresCompletadasDelCicloQueSeCierra() throws Exception {
        LocalDate cierreAnterior = hoy().minusDays(300);
        insertarCosechaAnterior(3, cierreAnterior);
        insertarLabor(3, "SIEMBRA", "COMPLETADA", cierreAnterior.minusDays(20), "7000.00");      // ciclo anterior: no suma
        insertarLabor(3, "FERTILIZACION", "COMPLETADA", hoy().minusDays(60), "10000.00");        // ciclo actual: suma
        insertarLabor(3, "PULVERIZACION", "COMPLETADA", hoy().minusDays(30), "2500.50");         // ciclo actual: suma
        insertarLabor(3, "PULVERIZACION", "CANCELADA", hoy().minusDays(20), "5000.00");          // no completada: no suma

        String body = mockMvc.perform(post("/api/cosechas")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 1, null, hoy(), "1500", "200")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rindeTonPorHa").value(10.0))
                .andExpect(jsonPath("$.ingresoBrutoTotal").value(300000.0))
                .andExpect(jsonPath("$.costosCicloImputados").value(12500.5))
                .andExpect(jsonPath("$.margenNetoCalculado").value(287499.5))
                // ROI = 287499.50 / 12500.50 * 100 = 2299.904 -> 2299.90
                .andExpect(jsonPath("$.roiPct").value(2299.9))
                .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(body, "$.id");
        mockMvc.perform(get("/api/cosechas/" + id).header("Authorization", bearer("operario1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margenNetoCalculado").value(287499.5));

        // Historial del lote: la nueva cosecha primero (más reciente).
        mockMvc.perform(get("/api/cosechas?loteId=3").header("Authorization", bearer("admin")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[1].fechaCosecha").value(cierreAnterior.toString()));
    }

    // ---------- Reglas de negocio ----------

    @Test
    void noSePuedeCosecharUnLoteQueNoEstaListoParaCosecha() throws Exception {
        int antes = contarCosechas();

        mockMvc.perform(post("/api/cosechas")
                        .header("Authorization", bearer("agronomo1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(1, 1, null, hoy(), "400", "300000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("LISTO_COSECHA")))
                .andExpect(jsonPath("$.message").value(containsString("SEMBRADO")));

        // La petición rechazada no modifica nada.
        assertEquals(antes, contarCosechas());
        mockMvc.perform(get("/api/lotes/1").header("Authorization", bearer("admin")))
                .andExpect(jsonPath("$.estado").value("SEMBRADO"));
    }

    @Test
    void laFechaDebeSerPosteriorALaUltimaCosechaDelLote() throws Exception {
        insertarCosechaAnterior(3, hoy().minusDays(5));

        mockMvc.perform(post("/api/cosechas")
                        .header("Authorization", bearer("agronomo1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy().minusDays(5), "100", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("posterior a la última cosecha")));
    }

    @Test
    void laLaborVinculadaDebeSerUnaCosechaCompletadaDelMismoLoteYNoReutilizada() throws Exception {
        String token = bearer("agronomo1");
        long trillaPlanificada = insertarLabor(3, "COSECHA", "PLANIFICADA", hoy().minusDays(2), "0");
        long pulverizacion = insertarLabor(3, "PULVERIZACION", "COMPLETADA", hoy().minusDays(40), "1000.00");
        long trilla = insertarLabor(3, "COSECHA", "COMPLETADA", hoy().minusDays(12), "4000.00");

        // Labor de otro lote (labor 1 = siembra del lote 1).
        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, 1L, hoy(), "100", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no pertenece al lote")));

        // Labor que no es de COSECHA.
        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, pulverizacion, hoy(), "100", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("debe ser de tipo COSECHA")));

        // Labor de COSECHA que todavía no se completó.
        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, trillaPlanificada, hoy(), "100", "1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("debe estar COMPLETADA")));

        // Fecha de cosecha anterior a la ejecución de la trilla.
        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, trilla, hoy().minusDays(13), "100", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("anterior a la ejecución")));

        // Correcta: queda vinculada y sus costos se imputan al ciclo.
        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, trilla, hoy().minusDays(10), "100", "1000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.laborId").value(trilla))
                .andExpect(jsonPath("$.costosCicloImputados").value(5000.0));

        // Nuevo ciclo listo para cosecha: la misma trilla no puede vincularse a otra cosecha.
        forzarEstadoLote(3, "LISTO_COSECHA");
        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, trilla, hoy(), "100", "1000")))
                .andExpect(status().isConflict());
    }

    // ---------- Seguridad, validaciones y 404 ----------

    @Test
    void elOperarioNoPuedeRegistrarCosechasYSinTokenEs401() throws Exception {
        mockMvc.perform(post("/api/cosechas")
                        .header("Authorization", bearer("operario1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy(), "100", "1000")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/cosechas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy(), "100", "1000")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/cultivos")).andExpect(status().isUnauthorized());
        assertEquals(0, contarCosechas());
    }

    @Test
    void rechazaDatosInvalidosConMensajesClaros() throws Exception {
        String token = bearer("admin");

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("El lote es obligatorio")))
                .andExpect(jsonPath("$.message").value(containsString("El cultivo es obligatorio")))
                .andExpect(jsonPath("$.message").value(containsString("La fecha de cosecha es obligatoria")));

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy(), "-1", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no pueden ser negativas")));

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy().plusDays(1), "100", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no puede ser futura")));

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, null, hoy(), "10.555", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("2 decimales")));

        assertEquals(0, contarCosechas());
    }

    @Test
    void referenciasInexistentesDevuelven404() throws Exception {
        String token = bearer("admin");

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(999, 3, null, hoy(), "100", "1000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Lote no encontrado")));

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 999, null, hoy(), "100", "1000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Cultivo no encontrado")));

        mockMvc.perform(post("/api/cosechas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(3, 3, 999L, hoy(), "100", "1000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Labor no encontrada")));

        mockMvc.perform(get("/api/cosechas/999").header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cosechas?loteId=999").header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
