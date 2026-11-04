package com.grupo107.agtech;

import com.grupo107.agtech.model.Cultivo;
import com.grupo107.agtech.model.EstadoLote;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.RegistroCosecha;
import com.grupo107.agtech.repository.CultivoRepository;
import com.grupo107.agtech.repository.InsumoRepository;
import com.grupo107.agtech.repository.LoteRepository;
import com.grupo107.agtech.repository.RegistroCosechaRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TableroFinancieroControllerTests {

    private static final String PASSWORD = "agtech123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoteRepository loteRepository;

    @Autowired
    private CultivoRepository cultivoRepository;

    @Autowired
    private RegistroCosechaRepository cosechaRepository;

    @Autowired
    private InsumoRepository insumoRepository;

    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private RegistroCosecha registrarCosechaFixture(Long loteId, String toneladas, String precioPorTon) {
        Lote lote = loteRepository.findById(loteId).orElseThrow();
        Cultivo soja = cultivoRepository.findById(1L).orElseThrow();

        BigDecimal ton = new BigDecimal(toneladas);
        BigDecimal precio = new BigDecimal(precioPorTon);
        BigDecimal rinde = ton.divide(lote.getSuperficieHa(), 2, RoundingMode.HALF_UP);
        BigDecimal ingresoBruto = ton.multiply(precio).setScale(2, RoundingMode.HALF_UP);

        lote.setEstado(EstadoLote.COSECHADO);
        loteRepository.save(lote);

        RegistroCosecha cosecha = new RegistroCosecha();
        cosecha.setLote(lote);
        cosecha.setCultivo(soja);
        cosecha.setFechaCosecha(LocalDate.of(2026, 10, 20));
        cosecha.setToneladasTotales(ton);
        cosecha.setRindeTonPorHa(rinde);
        cosecha.setPrecioVentaPorTon(precio);
        cosecha.setIngresoBrutoTotal(ingresoBruto);
        cosecha.setMargenNetoCalculado(ingresoBruto);
        return cosechaRepository.saveAndFlush(cosecha);
    }

    @Test
    void adminYAgronomoPuedenConsultarTableroYOperarioRecibe403() throws Exception {
        mockMvc.perform(get("/api/tablero-financiero"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/tablero-financiero")
                        .header("Authorization", bearer("operario1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tablero-financiero/lotes/1")
                        .header("Authorization", bearer("operario1")))
                .andExpect(status().isForbidden());

        for (String user : new String[]{"admin", "agronomo1"}) {
            mockMvc.perform(get("/api/tablero-financiero")
                            .header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalLotes").value(3))
                    .andExpect(jsonPath("$.lotes", hasSize(3)));
        }
    }

    @Test
    void lotesSinCosechaMuestranCostosAcumuladosDeLaboresCompletadasYRoiPendienteOSinCostos() throws Exception {
        String token = bearer("admin");

        // En data.sql:
        // Lote 1 tiene Labor #1 (COMPLETADA, $7231.50) y Labor #2 (COMPLETADA, $2587.30) -> total $9818.80
        // Lote 2 tiene Labor #3 (PLANIFICADA, $0.00) -> no suma al costo consolidado
        // Lote 3 no tiene labores -> $0.00
        mockMvc.perform(get("/api/tablero-financiero")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLotes").value(3))
                .andExpect(jsonPath("$.lotesConCosecha").value(0))
                .andExpect(jsonPath("$.lotesSinCosecha").value(3))
                .andExpect(jsonPath("$.totalLaboresCompletadas").value(2))
                .andExpect(jsonPath("$.costosTotalesAcumulados").value(9818.80))
                .andExpect(jsonPath("$.costoInsumosTotal").value(8978.80))
                .andExpect(jsonPath("$.costoMaquinariaTotal").value(840.00))
                .andExpect(jsonPath("$.ingresosBrutosTotales").value(0.00))
                .andExpect(jsonPath("$.roiGlobalPct").value(nullValue()))
                // Lote 1: costos > 0 y sin cosecha -> PENDIENTE (RF-08 criterio 2)
                .andExpect(jsonPath("$.lotes[0].loteId").value(1))
                .andExpect(jsonPath("$.lotes[0].cantidadLaboresCompletadas").value(2))
                .andExpect(jsonPath("$.lotes[0].costosTotalesAcumulados").value(9818.80))
                .andExpect(jsonPath("$.lotes[0].costoInsumosAcumulado").value(8978.80))
                .andExpect(jsonPath("$.lotes[0].costoMaquinariaAcumulado").value(840.00))
                .andExpect(jsonPath("$.lotes[0].costoPorHa").value(81.48))
                .andExpect(jsonPath("$.lotes[0].cosechaRegistrada").value(false))
                .andExpect(jsonPath("$.lotes[0].ingresoBrutoTotal").value(nullValue()))
                .andExpect(jsonPath("$.lotes[0].margenNeto").value(nullValue()))
                .andExpect(jsonPath("$.lotes[0].roiPct").value(nullValue()))
                .andExpect(jsonPath("$.lotes[0].estadoRoi").value("PENDIENTE"))
                .andExpect(jsonPath("$.lotes[0].mensajeRoi").value(containsStringIgnoringCase("pendiente")))
                .andExpect(jsonPath("$.lotes[0].laboresCompletadas", hasSize(2)))
                // Lote 2: labor PLANIFICADA no suma a costos completados -> SIN_COSTOS (RF-08 criterio 3)
                .andExpect(jsonPath("$.lotes[1].loteId").value(2))
                .andExpect(jsonPath("$.lotes[1].cantidadLaboresCompletadas").value(0))
                .andExpect(jsonPath("$.lotes[1].cantidadLaboresPendientes").value(1))
                .andExpect(jsonPath("$.lotes[1].costosTotalesAcumulados").value(0.00))
                .andExpect(jsonPath("$.lotes[1].roiPct").value(nullValue()))
                .andExpect(jsonPath("$.lotes[1].estadoRoi").value("SIN_COSTOS"))
                .andExpect(jsonPath("$.lotes[1].mensajeRoi").value(containsStringIgnoringCase("sin costos registrados")));
    }

    @Test
    void loteConCosechaYCostosCalculaIngresoBrutoMargenNetoYRoiPorcentual() throws Exception {
        // Lote 1 tiene $9,818.80 de costos acumulados.
        // Cosecha: 420 ton * $310/ton = $130,200.00
        // Margen Neto = 130,200.00 - 9,818.80 = 120,381.20
        // ROI (%) = (120,381.20 / 9,818.80) * 100 = 1226.03 %
        registrarCosechaFixture(1L, "420.00", "310.00");

        String token = bearer("agronomo1");

        mockMvc.perform(get("/api/tablero-financiero/lotes/1")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loteId").value(1))
                .andExpect(jsonPath("$.cosechaRegistrada").value(true))
                .andExpect(jsonPath("$.cultivoNombre").value("Soja 1°"))
                .andExpect(jsonPath("$.toneladasTotales").value(420.00))
                .andExpect(jsonPath("$.rindeTonPorHa").value(3.49))
                .andExpect(jsonPath("$.costosTotalesAcumulados").value(9818.80))
                .andExpect(jsonPath("$.ingresoBrutoTotal").value(130200.00))
                .andExpect(jsonPath("$.margenNeto").value(120381.20))
                .andExpect(jsonPath("$.roiPct").value(1226.03))
                .andExpect(jsonPath("$.estadoRoi").value("CALCULADO"))
                .andExpect(jsonPath("$.cosechas", hasSize(1)));

        mockMvc.perform(get("/api/tablero-financiero")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotesConCosecha").value(1))
                .andExpect(jsonPath("$.ingresosBrutosTotales").value(130200.00))
                .andExpect(jsonPath("$.costosLotesConCosecha").value(9818.80))
                .andExpect(jsonPath("$.margenNetoLotesCosechados").value(120381.20))
                .andExpect(jsonPath("$.roiLotesCosechadosPct").value(1226.03));
    }

    @Test
    void loteConPerdidaCalculaMargenNetoYRoiNegativos() throws Exception {
        // Lote 1 tiene $9,818.80 de costos acumulados.
        // Cosecha baja por sequía: 20 ton * $300/ton = $6,000.00
        // Margen Neto = 6,000.00 - 9,818.80 = -3,818.80
        // ROI (%) = (-3,818.80 / 9,818.80) * 100 = -38.89 %
        registrarCosechaFixture(1L, "20.00", "300.00");

        mockMvc.perform(get("/api/tablero-financiero/lotes/1")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresoBrutoTotal").value(6000.00))
                .andExpect(jsonPath("$.costosTotalesAcumulados").value(9818.80))
                .andExpect(jsonPath("$.margenNeto").value(-3818.80))
                .andExpect(jsonPath("$.roiPct").value(-38.89))
                .andExpect(jsonPath("$.estadoRoi").value("CALCULADO"));
    }

    @Test
    void loteConCosechaPeroSinCostosRegistradosEvitaDivisionPorCeroYReportaSinCostos() throws Exception {
        // Lote 3 no tiene labores completadas ($0.00 de costos).
        registrarCosechaFixture(3L, "500.00", "200.00");

        mockMvc.perform(get("/api/tablero-financiero/lotes/3")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loteId").value(3))
                .andExpect(jsonPath("$.cosechaRegistrada").value(true))
                .andExpect(jsonPath("$.costosTotalesAcumulados").value(0.00))
                .andExpect(jsonPath("$.ingresoBrutoTotal").value(100000.00))
                .andExpect(jsonPath("$.margenNeto").value(100000.00))
                .andExpect(jsonPath("$.roiPct").value(nullValue()))
                .andExpect(jsonPath("$.estadoRoi").value("SIN_COSTOS"))
                .andExpect(jsonPath("$.mensajeRoi").value("Sin costos registrados"));
    }

    @Test
    void costeoHistoricoPermaneceInmutableAunqueCambieElPrecioDelInsumo() throws Exception {
        // RN-05: cambiar el precio actual del insumo no altera los costos históricos acumulados del lote.
        Insumo semilla = insumoRepository.findById(1L).orElseThrow();
        semilla.setPrecioUnitario(new BigDecimal("999.99"));
        insumoRepository.saveAndFlush(semilla);

        mockMvc.perform(get("/api/tablero-financiero/lotes/1")
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costosTotalesAcumulados").value(9818.80))
                .andExpect(jsonPath("$.costoInsumosAcumulado").value(8978.80));
    }

    @Test
    void permiteFiltrarPorEstadoDeLoteYSoloConCosechaYDevuelve404SiLoteNoExiste() throws Exception {
        registrarCosechaFixture(1L, "400.00", "300.00");
        String token = bearer("agronomo1");

        mockMvc.perform(get("/api/tablero-financiero/lotes")
                        .param("soloConCosecha", "true")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].loteId").value(1));

        mockMvc.perform(get("/api/tablero-financiero/lotes")
                        .param("estadoLote", "EN_PREPARACION")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].loteId").value(2));

        mockMvc.perform(get("/api/tablero-financiero/lotes/999")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
