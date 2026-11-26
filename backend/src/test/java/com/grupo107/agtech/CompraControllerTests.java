package com.grupo107.agtech;

import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.repository.CompraInsumoRepository;
import com.grupo107.agtech.repository.InsumoRepository;
import com.grupo107.agtech.repository.LaborAgricolaRepository;
import com.grupo107.agtech.repository.MovimientoStockRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompraControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InsumoRepository insumoRepository;

    @Autowired
    private CompraInsumoRepository compraInsumoRepository;

    @Autowired
    private MovimientoStockRepository movimientoStockRepository;

    @Autowired
    private LaborAgricolaRepository laborAgricolaRepository;

    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String compraJson(long proveedorId, long insumoId, String cantidad, String precio) {
        return """
                {
                  "proveedorId": %d,
                  "fechaCompra": "2026-11-14T10:00:00",
                  "detalles": [
                    {"insumoId": %d, "cantidad": %s, "precioUnitario": %s}
                  ]
                }
                """.formatted(proveedorId, insumoId, cantidad, precio);
    }

    // --- Registro exitoso ---

    @Test
    void adminRegistraCompraYSeActualizaStockYPrecio() throws Exception {
        mockMvc.perform(get("/api/insumos/5").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(45.0))
                .andExpect(jsonPath("$.precioUnitario").value(32.0));

        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "100.00", "30.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCompra").value(3000.0))
                .andExpect(jsonPath("$.proveedorNombre").value("Semilleria Don Mario"))
                .andExpect(jsonPath("$.usuarioUsername").value("admin"))
                .andExpect(jsonPath("$.detalles", hasSize(1)))
                .andExpect(jsonPath("$.detalles[0].insumoNombre").value("Fungicida Curalotodo Pro"))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(3000.0));

        mockMvc.perform(get("/api/insumos/5").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(145.0))
                .andExpect(jsonPath("$.precioUnitario").value(30.0));

        mockMvc.perform(get("/api/insumos/5/movimientos").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipo == 'INGRESO_COMPRA')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.tipo == 'INGRESO_COMPRA')].cantidad", contains(100.0)));
    }

    @Test
    void compraConMultiplesLineasCalculaTotalCorrectamente() throws Exception {
        String body = """
                {
                  "proveedorId": 2,
                  "fechaCompra": "2026-11-14T10:00:00",
                  "detalles": [
                    {"insumoId": 5, "cantidad": 100, "precioUnitario": 30},
                    {"insumoId": 2, "cantidad": 500, "precioUnitario": 9}
                  ]
                }
                """;
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCompra").value(7500.0))
                .andExpect(jsonPath("$.detalles", hasSize(2)));
    }

    @Test
    void compraMultiLineaConFallaEnSegundaLineaHaceRollbackAtomico() throws Exception {
        Insumo insumo5Antes = insumoRepository.findById(5L).orElseThrow();
        BigDecimal stockAntes = insumo5Antes.getStockActual();
        BigDecimal precioAntes = insumo5Antes.getPrecioUnitario();
        long totalComprasAntes = compraInsumoRepository.count();
        int movsAntes = movimientoStockRepository.findByInsumoIdOrderByFechaDescIdDesc(5L).size();

        String body = """
                {
                  "proveedorId": 2,
                  "fechaCompra": "2026-11-14T10:00:00",
                  "detalles": [
                    {"insumoId": 5, "cantidad": 50, "precioUnitario": 25},
                    {"insumoId": 99999, "cantidad": 10, "precioUnitario": 15}
                  ]
                }
                """;

        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        Insumo insumo5Despues = insumoRepository.findById(5L).orElseThrow();
        assertEquals(0, stockAntes.compareTo(insumo5Despues.getStockActual()));
        assertEquals(0, precioAntes.compareTo(insumo5Despues.getPrecioUnitario()));
        assertEquals(totalComprasAntes, compraInsumoRepository.count());
        assertEquals(movsAntes, movimientoStockRepository.findByInsumoIdOrderByFechaDescIdDesc(5L).size());
    }

    @Test
    void cambioDePrecioPorCompraNoAlteraCostoHistoricoDeLaborCompletada() throws Exception {
        LaborAgricola completada = laborAgricolaRepository.findAll().stream()
                .filter(l -> l.getEstado() == EstadoLabor.COMPLETADA)
                .findFirst()
                .orElseThrow();
        BigDecimal costoHistoricoAntes = completada.getCostoTotalCalculado();

        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "1.00", "9999.99")))
                .andExpect(status().isCreated());

        LaborAgricola completadaDespues = laborAgricolaRepository.findById(completada.getId()).orElseThrow();
        assertEquals(0, costoHistoricoAntes.compareTo(completadaDespues.getCostoTotalCalculado()));
    }

    // --- Autorización (matriz doc 07 §7.2) ---

    @Test
    void operarioRecibe403AlRegistrarCompra() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("operario1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "10", "30")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operarioRecibe403AlListarComprasYProveedores() throws Exception {
        mockMvc.perform(get("/api/compras").header("Authorization", bearer("operario1")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/proveedores").header("Authorization", bearer("operario1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void agronomoTambienRecibe403AlRegistrarCompra() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("agronomo1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "10", "30")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "10", "30")))
                .andExpect(status().isUnauthorized());
    }

    // --- Validaciones de entrada ---

    @Test
    void cantidadCeroOPrecioNegativoDevuelven400() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "0", "30")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "10", "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compraConInsumoRepetidoDevuelve400() throws Exception {
        String body = """
                {
                  "proveedorId": 2,
                  "fechaCompra": "2026-11-14T10:00:00",
                  "detalles": [
                    {"insumoId": 5, "cantidad": 10, "precioUnitario": 30},
                    {"insumoId": 5, "cantidad": 20, "precioUnitario": 30}
                  ]
                }
                """;
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No se puede repetir")));
    }

    @Test
    void compraSinLineasDevuelve400() throws Exception {
        String body = """
                {"proveedorId": 2, "fechaCompra": "2026-11-14T10:00:00", "detalles": []}
                """;
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fechaFuturaEnCompraDevuelve400() throws Exception {
        String body = """
                {
                  "proveedorId": 2,
                  "fechaCompra": "%s",
                  "detalles": [
                    {"insumoId": 5, "cantidad": 10, "precioUnitario": 30}
                  ]
                }
                """.formatted(LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void desbordeDecimal12Devuelve400() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 5L, "1000000.00", "100000.00")))
                .andExpect(status().isBadRequest());
    }

    // --- Recursos inexistentes ---

    @Test
    void insumoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(2L, 99999L, "10", "30")))
                .andExpect(status().isNotFound());
    }

    @Test
    void proveedorInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post("/api/compras")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compraJson(99999L, 5L, "10", "30")))
                .andExpect(status().isNotFound());
    }

    @Test
    void obtenerCompraInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/compras/99999").header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound());
    }

    // --- Consultas ---

    @Test
    void adminYAgronomoPuedenListarCompras() throws Exception {
        for (String user : new String[]{"admin", "agronomo1"}) {
            mockMvc.perform(get("/api/compras").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        }
    }

    @Test
    void obtenerCompraPorIdDevuelveDetalleCompleto() throws Exception {
        mockMvc.perform(get("/api/compras/1").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.proveedorNombre").value("Agroinsumos del Centro"))
                .andExpect(jsonPath("$.detalles", hasSize(1)));
    }

    @Test
    void listarProveedoresDevuelveElCatalogoDelSeed() throws Exception {
        mockMvc.perform(get("/api/proveedores").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].nombre",
                        containsInAnyOrder("Agroinsumos del Centro", "Semilleria Don Mario")));
    }
}
