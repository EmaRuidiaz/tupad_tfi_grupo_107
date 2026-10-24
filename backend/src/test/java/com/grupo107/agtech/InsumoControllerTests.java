package com.grupo107.agtech;

import com.grupo107.agtech.repository.MovimientoStockRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InsumoControllerTests {

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
    void cualquierRolAutenticadoPuedeListarInsumosYVerIndicadorDeStockCritico() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/insumos").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$[1].nombre").value("Glifosato Roundup Control 48%"))
                    .andExpect(jsonPath("$[1].stockActual").value(1200.0))
                    .andExpect(jsonPath("$[1].stockReservado").value(170.0))
                    .andExpect(jsonPath("$[1].stockDisponible").value(1030.0))
                    .andExpect(jsonPath("$[1].stockCritico").value(false))
                    .andExpect(jsonPath("$[4].nombre").value("Fungicida Curalotodo Pro"))
                    .andExpect(jsonPath("$[4].stockActual").value(45.0))
                    .andExpect(jsonPath("$[4].stockMinimoAlerta").value(80.0))
                    .andExpect(jsonPath("$[4].stockCritico").value(true));
        }
    }

    @Test
    void filtroStockCriticoDevuelveSoloInsumosEnAlerta() throws Exception {
        mockMvc.perform(get("/api/insumos")
                        .param("stockCritico", "true")
                        .header("Authorization", bearer("agronomo1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Fungicida Curalotodo Pro"))
                .andExpect(jsonPath("$[0].stockCritico").value(true));
    }

    @Test
    void crearInsumoConStockInicialRegistraMovimientoDeStockInicialAutomatico() throws Exception {
        String tokenAdmin = bearer("admin");

        String response = mockMvc.perform(post("/api/insumos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Urea Granulada 46%",
                                  "categoria": "FERTILIZANTE",
                                  "unidadMedida": "BOLSAS",
                                  "stockInicial": 200.00,
                                  "stockMinimoAlerta": 50.00,
                                  "precioUnitario": 42.50
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Urea Granulada 46%"))
                .andExpect(jsonPath("$.stockActual").value(200.0))
                .andExpect(jsonPath("$.stockReservado").value(0.0))
                .andExpect(jsonPath("$.stockDisponible").value(200.0))
                .andExpect(jsonPath("$.stockCritico").value(false))
                .andReturn().getResponse().getContentAsString();

        Integer idCreado = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/insumos/" + idCreado + "/movimientos")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$[0].cantidad").value(200.0))
                .andExpect(jsonPath("$[0].usuarioUsername").value("admin"))
                .andExpect(jsonPath("$[0].observacion").value("Stock inicial"));
    }

    @Test
    void agronomoYOperarioReciben403AlIntentarCrearEditarEliminarOAjustarInsumos() throws Exception {
        String payload = """
                {
                  "nombre": "Insumo No Autorizado",
                  "categoria": "SEMILLA",
                  "unidadMedida": "BOLSAS",
                  "stockInicial": 10,
                  "stockMinimoAlerta": 5,
                  "precioUnitario": 100
                }
                """;

        for (String user : new String[]{"agronomo1", "operario1"}) {
            String token = bearer(user);
            mockMvc.perform(post("/api/insumos").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/insumos/1").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/insumos/1").header("Authorization", token))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/insumos/1/ajustes").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cantidad\":10,\"observacion\":\"Intento no autorizado\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void actualizarInsumoModificaCatalogoYUmbralSinAlterarStockFisico() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(put("/api/insumos/1")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Semilla Soja Don Mario 46i20 Editada",
                                  "categoria": "SEMILLA",
                                  "unidadMedida": "BOLSAS",
                                  "stockMinimoAlerta": 500.00,
                                  "precioUnitario": 60.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Semilla Soja Don Mario 46i20 Editada"))
                .andExpect(jsonPath("$.stockActual").value(450.0))
                .andExpect(jsonPath("$.stockMinimoAlerta").value(500.0))
                .andExpect(jsonPath("$.precioUnitario").value(60.0))
                .andExpect(jsonPath("$.stockCritico").value(true));
    }

    @Test
    void ajusteDeStockActualizaStockActualYRegistraAuditoriasValidaRestricciones() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(post("/api/insumos/5/ajustes")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\": 50.00, \"observacion\": \"Recuento físico de depósito\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.cantidad").value(50.0))
                .andExpect(jsonPath("$.usuarioUsername").value("admin"))
                .andExpect(jsonPath("$.observacion").value("Recuento físico de depósito"));

        mockMvc.perform(get("/api/insumos/5").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(95.0))
                .andExpect(jsonPath("$.stockCritico").value(false));

        mockMvc.perform(post("/api/insumos/5/ajustes")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\": -10.00, \"observacion\": \"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/insumos/5/ajustes")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\": -500.00, \"observacion\": \"Baja excesiva\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/insumos/2/ajustes")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\": -1100.00, \"observacion\": \"Comprometería stock reservado\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminarInsumoSinReferenciasDevuelve204YConReferenciasDevuelve409() throws Exception {
        String tokenAdmin = bearer("admin");

        String body = mockMvc.perform(post("/api/insumos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Insumo Temporal",
                                  "categoria": "COMBUSTIBLE",
                                  "unidadMedida": "LITROS",
                                  "stockInicial": 0,
                                  "stockMinimoAlerta": 10,
                                  "precioUnitario": 1.5
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer idTemporal = JsonPath.read(body, "$.id");

        mockMvc.perform(delete("/api/insumos/" + idTemporal).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/insumos/" + idTemporal).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/insumos/1").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void valoresNumericosFueraDeLaPrecisionDeLaBaseDevuelven400() throws Exception {
        String tokenAdmin = bearer("admin");

        mockMvc.perform(post("/api/insumos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Insumo con precio inválido",
                                  "categoria": "SEMILLA",
                                  "unidadMedida": "BOLSAS",
                                  "stockMinimoAlerta": 10,
                                  "precioUnitario": 1.234
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("precio unitario")));

        mockMvc.perform(post("/api/insumos/3/ajustes")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\": 12345678901, \"observacion\": \"Fuera de rango\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cantidad del ajuste")));
    }

    @Test
    void noSePuedeEliminarUnInsumoQueTodaviaTieneStock() throws Exception {
        mockMvc.perform(delete("/api/insumos/3").header("Authorization", bearer("admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("todavía tiene stock")));

        mockMvc.perform(get("/api/insumos/3").header("Authorization", bearer("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void datosSemillaCumplenInvarianteStockActualIgualASumaDeMovimientos() {
        Integer inconsistentes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM insumo i
                WHERE i.stock_actual <> COALESCE(
                    (SELECT SUM(m.cantidad) FROM movimiento_stock m WHERE m.insumo_id = i.id), 0)
                """, Integer.class);
        assertEquals(0, inconsistentes,
                "Todo insumo debe cumplir stock_actual = SUM(movimiento_stock.cantidad)");
    }

    @Test
    void repositorioDeMovimientosNoExponeOperacionesDeBorradoPorInmutabilidadRN09() {
        boolean tieneDelete = Arrays.stream(MovimientoStockRepository.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.startsWith("delete"));
        assertFalse(tieneDelete, "MovimientoStockRepository no debe exponer operaciones delete");
    }
}
