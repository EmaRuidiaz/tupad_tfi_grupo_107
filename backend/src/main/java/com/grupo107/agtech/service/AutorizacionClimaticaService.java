package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.LaborResponse;
import com.grupo107.agtech.controller.dto.ValidacionClimaResponse;
import com.grupo107.agtech.model.EstadoLabor;
import com.grupo107.agtech.model.LaborAgricola;
import com.grupo107.agtech.model.Lote;
import com.grupo107.agtech.model.ReglaClimatica;
import com.grupo107.agtech.model.TipoLabor;
import com.grupo107.agtech.repository.LaborAgricolaRepository;
import com.grupo107.agtech.repository.ReglaClimaticaRepository;
import com.grupo107.agtech.service.clima.ClimaNoDisponibleException;
import com.grupo107.agtech.service.clima.ConsultaClima;
import com.grupo107.agtech.service.clima.LecturaClima;
import com.grupo107.agtech.service.clima.ProveedorClima;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Autorización climática de labores (HU-10, CU-04, RN-01 y RN-11).
 *
 * <p>La consulta a la API climática se hace fuera de toda transacción para no retener conexiones
 * ni bloqueos mientras se espera al proveedor (hasta ~10 s con reintentos). Después, en una
 * transacción corta, se bloquea la labor ({@code FOR UPDATE}), se vuelve a verificar su estado
 * y se aplica el resultado. Si el clima no está disponible la labor no cambia (RF-06, criterio 5).
 */
@Service
public class AutorizacionClimaticaService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LaborAgricolaRepository laborRepository;
    private final ReglaClimaticaRepository reglaRepository;
    private final ProveedorClima proveedorClima;
    private final TransactionTemplate transactionTemplate;

    public AutorizacionClimaticaService(LaborAgricolaRepository laborRepository,
                                        ReglaClimaticaRepository reglaRepository,
                                        ProveedorClima proveedorClima,
                                        TransactionTemplate transactionTemplate) {
        this.laborRepository = laborRepository;
        this.reglaRepository = reglaRepository;
        this.proveedorClima = proveedorClima;
        this.transactionTemplate = transactionTemplate;
    }

    /** Valida el clima actual del lote contra la regla del tipo de labor y aprueba o bloquea la labor. */
    public ValidacionClimaResponse validarClima(Long laborId) {
        Coordenadas lote = transactionTemplate.execute(status -> {
            LaborAgricola labor = buscarLabor(laborId);
            verificarValidable(labor);
            buscarRegla(labor.getTipoLabor());
            return Coordenadas.de(labor.getLote());
        });

        ConsultaClima consulta = proveedorClima.obtenerClimaActual(lote.latitud(), lote.longitud());
        LecturaClima lectura = consulta.lectura();
        verificarLecturaCompleta(lectura);

        return transactionTemplate.execute(status -> {
            LaborAgricola labor = buscarLaborParaActualizar(laborId);
            verificarValidable(labor);
            ReglaClimatica regla = buscarRegla(labor.getTipoLabor());

            List<String> incumplimientos = regla.evaluar(
                    lectura.vientoVelocidadKmh(),
                    lectura.temperaturaC(),
                    lectura.probabilidadLluviaPct(),
                    lectura.humedadRelativaPct());

            labor.setTemperaturaRegistrada(escala2(lectura.temperaturaC()));
            labor.setVientoVelocidadKmh(escala2(lectura.vientoVelocidadKmh()));
            labor.setProbabilidadLluviaPct(escala2(lectura.probabilidadLluviaPct()));
            labor.setHumedadRegistradaPct(escala2(lectura.humedadRelativaPct()));
            labor.setEstado(incumplimientos.isEmpty() ? EstadoLabor.APROBADA_CLIMA : EstadoLabor.BLOQUEADA_CLIMA);

            LaborResponse actualizada = LaborResponse.from(laborRepository.save(labor));
            return ValidacionClimaResponse.evaluada(actualizada, consulta, incumplimientos, regla.getMensajeAlerta());
        });
    }

    /**
     * RN-11: sólo si la API climática no responde y no hay lectura en caché, un AGRONOMO o ADMIN
     * puede aprobar la labor manualmente dejando la justificación en {@code observaciones_climaticas}.
     */
    public ValidacionClimaResponse autorizarManualmente(Long laborId, String justificacion, String username) {
        Coordenadas lote = transactionTemplate.execute(status -> {
            LaborAgricola labor = buscarLabor(laborId);
            verificarValidable(labor);
            return Coordenadas.de(labor.getLote());
        });

        try {
            proveedorClima.obtenerClimaActual(lote.latitud(), lote.longitud());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El servicio climático está disponible: la labor debe validarse con los datos actuales"
                            + " (la autorización manual sólo procede ante una contingencia climática, RN-11)");
        } catch (ClimaNoDisponibleException ex) {
            // Contingencia confirmada: no hay API ni caché vigente.
        }

        return transactionTemplate.execute(status -> {
            LaborAgricola labor = buscarLaborParaActualizar(laborId);
            verificarValidable(labor);

            String registro = "Autorización manual por contingencia climática (" + username + ", "
                    + LocalDateTime.now().format(FORMATO_FECHA) + "): " + justificacion.trim();
            String previas = labor.getObservacionesClimaticas();
            labor.setObservacionesClimaticas(previas == null || previas.isBlank() ? registro : previas + " | " + registro);
            labor.setTemperaturaRegistrada(null);
            labor.setVientoVelocidadKmh(null);
            labor.setProbabilidadLluviaPct(null);
            labor.setHumedadRegistradaPct(null);
            labor.setEstado(EstadoLabor.APROBADA_CLIMA);

            return ValidacionClimaResponse.manual(LaborResponse.from(laborRepository.save(labor)));
        });
    }

    // doc 06 §6.3.2: se valida desde PLANIFICADA o BLOQUEADA_CLIMA (revalidación); LABRANZA no lleva regla (RN-01).
    private void verificarValidable(LaborAgricola labor) {
        if (!labor.getTipoLabor().requiereValidacionClimatica()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Las labores de " + labor.getTipoLabor() + " no requieren validación climática (RN-01):"
                            + " pueden iniciarse directamente");
        }
        EstadoLabor estado = labor.getEstado();
        if (estado == EstadoLabor.APROBADA_CLIMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La labor ya cuenta con aprobación climática: puede iniciarse");
        }
        if (estado != EstadoLabor.PLANIFICADA && estado != EstadoLabor.BLOQUEADA_CLIMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede validar el clima de una labor en estado " + estado
                            + " (sólo PLANIFICADA o BLOQUEADA_CLIMA)");
        }
    }

    private ReglaClimatica buscarRegla(TipoLabor tipoLabor) {
        return reglaRepository.findByTipoLabor(tipoLabor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay una regla climática configurada para " + tipoLabor
                                + ": un administrador debe darla de alta antes de validar la labor"));
    }

    private static void verificarLecturaCompleta(LecturaClima lectura) {
        if (lectura.temperaturaC() == null || lectura.vientoVelocidadKmh() == null
                || lectura.probabilidadLluviaPct() == null || lectura.humedadRelativaPct() == null) {
            throw new ClimaNoDisponibleException(
                    "Validación climática no disponible: el proveedor meteorológico devolvió una lectura incompleta."
                            + " Reintentá en unos minutos.", null);
        }
    }

    private record Coordenadas(BigDecimal latitud, BigDecimal longitud) {
        static Coordenadas de(Lote lote) {
            return new Coordenadas(lote.getLatitud(), lote.getLongitud());
        }
    }

    private static BigDecimal escala2(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private LaborAgricola buscarLabor(Long id) {
        return laborRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Labor agrícola no encontrada con id: " + id));
    }

    private LaborAgricola buscarLaborParaActualizar(Long id) {
        return laborRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Labor agrícola no encontrada con id: " + id));
    }
}
