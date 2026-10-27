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
class LoteControllerTests {

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

    private String loteJson(String nombre, String superficie, String latitud, String longitud, String poligonoWkt) {
        String poligono = poligonoWkt == null ? "null" : "\"" + poligonoWkt + "\"";
        return """
                {
                  "nombre": "%s",
                  "superficieHa": %s,
                  "latitud": %s,
                  "longitud": %s,
                  "poligonoWkt": %s,
                  "tipoSuelo": "Franco Limoso",
                  "observaciones": "Lote de prueba"
                }
                """.formatted(nombre, superficie, latitud, longitud, poligono);
    }

    @Test
    void cualquierRolAutenticadoPuedeListarLotesYFiltrarPorEstado() throws Exception {
        for (String user : new String[]{"admin", "agronomo1", "operario1"}) {
            mockMvc.perform(get("/api/lotes").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].nombre").value("Lote Norte - La Escondida"))
                    .andExpect(jsonPath("$[0].superficieHa").value(120.5))
                    .andExpect(jsonPath("$[0].estado").value("SEMBRADO"))
                    .andExpect(jsonPath("$[0].transicionesPermitidas", containsInAnyOrder("EN_CRECIMIENTO", "EN_PREPARACION")))
                    .andExpect(jsonPath("$[0].usuarioUsername").value("agronomo1"));
        }

        mockMvc.perform(get("/api/lotes")
                        .param("estado", "LISTO_COSECHA")
                        .header("Authorization", bearer("operario1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Lote Este - Las Acacias"));
    }

    @Test
    void agronomoCreaLoteQueQuedaEnPreparacionYAsociadoAlUsuarioDelToken() throws Exception {
        mockMvc.perform(post("/api/lotes")
                        .header("Authorization", bearer("agronomo1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("Lote Oeste - El Molino", "64.25", "-33.9", "-60.6",
                                "POLYGON((-60.61 -33.91, -60.60 -33.91, -60.60 -33.90, -60.61 -33.90, -60.61 -33.91))")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Lote Oeste - El Molino"))
                .andExpect(jsonPath("$.superficieHa").value(64.25))
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"))
                .andExpect(jsonPath("$.transicionesPermitidas", hasSize(0)))
                .andExpect(jsonPath("$.usuarioUsername").value("agronomo1"));
    }

    @Test
    void operarioRecibe403AlIntentarCrearEditarEliminarOCambiarEstado() throws Exception {
        String token = bearer("operario1");
        String payload = loteJson("Lote No Autorizado", "10", "-33.9", "-60.6", null);

        mockMvc.perform(post("/api/lotes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/lotes/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/lotes/3").header("Authorization", token))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/lotes/1/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_CRECIMIENTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nombreDuplicadoSuperficieNoPositivaYCoordenadasFueraDeRangoDevuelven400() throws Exception {
        String token = bearer("admin");

        mockMvc.perform(post("/api/lotes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("  lote norte - la escondida ", "50", "-33.9", "-60.6", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Ya existe un lote")));

        mockMvc.perform(post("/api/lotes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("Lote Superficie Cero", "0", "-33.9", "-60.6", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("superficie debe ser mayor a 0")));

        mockMvc.perform(post("/api/lotes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("Lote Fuera De Rango", "10", "-95", "-200", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("latitud debe estar entre -90 y 90"),
                        containsString("longitud debe estar entre -180 y 180"))));
    }

    @Test
    void poligonoWktMalFormadoSeRechazaIndicandoElError() throws Exception {
        String token = bearer("admin");
        String[][] casos = {
                {"LINESTRING(-60.6 -33.9, -60.5 -33.8)", "POLYGON((lon lat"},
                {"POLYGON((-60.6 -33.9, -60.5 -33.9, -60.5 -33.8, -60.6 -33.8))", "cerrado"},
                {"POLYGON((-60.6 -33.9, -60.5 -33.9, -60.6 -33.9))", "al menos 4 puntos"},
                {"POLYGON((-60.6 -33.9, abc -33.9, -60.5 -33.8, -60.6 -33.9))", "no numérica"},
                {"POLYGON((-60.6 -33.9, -60.5 -95, -60.5 -33.8, -60.6 -33.9))", "fuera de rango"},
        };

        for (String[] caso : casos) {
            mockMvc.perform(post("/api/lotes").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loteJson("Lote WKT", "10", "-33.9", "-60.6", caso[0])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(allOf(
                            containsString("polígono WKT es inválido"),
                            containsString(caso[1]))));
        }
    }

    @Test
    void actualizarLoteModificaDatosSinAlterarEstadoNiPermitirNombreDeOtroLote() throws Exception {
        String token = bearer("agronomo1");

        mockMvc.perform(put("/api/lotes/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("Lote Norte - La Escondida", "130.75", "-33.893", "-60.574", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Lote Norte - La Escondida"))
                .andExpect(jsonPath("$.superficieHa").value(130.75))
                .andExpect(jsonPath("$.poligonoWkt").doesNotExist())
                .andExpect(jsonPath("$.estado").value("SEMBRADO"));

        mockMvc.perform(put("/api/lotes/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("Lote Sur - El Bajío", "130.75", "-33.893", "-60.574", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Ya existe un lote")));

        mockMvc.perform(put("/api/lotes/999").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("Lote Inexistente", "10", "-33.9", "-60.6", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarLoteSinLaboresDevuelve204YConLaboresDevuelve409() throws Exception {
        String token = bearer("admin");

        mockMvc.perform(delete("/api/lotes/1").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("labores o cosechas")));

        mockMvc.perform(delete("/api/lotes/3").header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/lotes/3").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambioDeEstadoRespetaLasTransicionesManualesPermitidas() throws Exception {
        String token = bearer("agronomo1");

        mockMvc.perform(patch("/api/lotes/1/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_CRECIMIENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CRECIMIENTO"));

        mockMvc.perform(patch("/api/lotes/1/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"LISTO_COSECHA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO_COSECHA"))
                .andExpect(jsonPath("$.transicionesPermitidas", hasSize(0)));

        mockMvc.perform(patch("/api/lotes/1/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COSECHADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("LISTO_COSECHA -> COSECHADO")));

        mockMvc.perform(patch("/api/lotes/2/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SEMBRADO\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/lotes/2/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void perdidaTotalRequiereObservacionYQuedaRegistradaEnElLote() throws Exception {
        String token = bearer("admin");

        mockMvc.perform(patch("/api/lotes/1/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PREPARACION\",\"observacion\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("pérdida total")));

        mockMvc.perform(patch("/api/lotes/1/estado").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PREPARACION\",\"observacion\":\"Granizo destruyó el cultivo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"))
                .andExpect(jsonPath("$.observaciones").value(allOf(
                        startsWith("Sembrado con Soja 1.ª. Buen drenaje."),
                        containsString("SEMBRADO -> EN_PREPARACION: Granizo destruyó el cultivo"))));
    }
}
