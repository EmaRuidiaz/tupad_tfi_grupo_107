package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.CompraRequest;
import com.grupo107.agtech.controller.dto.CompraResponse;
import com.grupo107.agtech.controller.dto.DetalleCompraRequest;
import com.grupo107.agtech.controller.dto.ProveedorResponse;
import com.grupo107.agtech.model.CompraInsumo;
import com.grupo107.agtech.model.DetalleCompra;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.MovimientoStock;
import com.grupo107.agtech.model.Proveedor;
import com.grupo107.agtech.model.TipoMovimientoStock;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.CompraInsumoRepository;
import com.grupo107.agtech.repository.InsumoRepository;
import com.grupo107.agtech.repository.MovimientoStockRepository;
import com.grupo107.agtech.repository.ProveedorRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-02 / RF-03: registro de compras de insumos a proveedores.
 *
 * <p>La operación es atómica: bloquea los insumos con {@code FOR UPDATE} en orden ascendente
 * de {@code insumo_id} (RN-13), actualiza {@code stock_actual}, {@code precio_unitario} y
 * registra un {@code INGRESO_COMPRA} en {@code movimiento_stock} por cada línea.
 */
@Service
public class CompraService {

    private final CompraInsumoRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final InsumoRepository insumoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;
    private static final int ENTEROS_MAX_DECIMAL_12_2 = 10;

    public CompraService(CompraInsumoRepository compraRepository,
                         ProveedorRepository proveedorRepository,
                         InsumoRepository insumoRepository,
                         MovimientoStockRepository movimientoStockRepository,
                         UsuarioRepository usuarioRepository) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.insumoRepository = insumoRepository;
        this.movimientoStockRepository = movimientoStockRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<CompraResponse> listar() {
        return compraRepository.findAllByOrderByFechaCompraDescIdDesc().stream()
                .map(CompraResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompraResponse obtenerPorId(Long id) {
        return compraRepository.findConDetalleById(id)
                .map(CompraResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Compra no encontrada con id: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listarProveedores() {
        return proveedorRepository.findAllByOrderByNombreAsc().stream()
                .map(ProveedorResponse::from)
                .toList();
    }

    @Transactional
    public CompraResponse registrar(CompraRequest request, String username) {
        if (request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La compra debe tener al menos una línea de detalle");
        }

        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado con id: " + request.getProveedorId()));
        Usuario usuario = buscarUsuario(username);

        // No se puede repetir el mismo insumo en la misma compra
        Set<Long> idsVistos = new HashSet<>();
        for (DetalleCompraRequest d : request.getDetalles()) {
            if (!idsVistos.add(d.getInsumoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se puede repetir el mismo insumo en la misma compra (insumo id: "
                                + d.getInsumoId() + ")");
            }
        }

        // Orden ascendente por insumo_id para prevenir deadlocks (RN-13)
        List<DetalleCompraRequest> ordenados = request.getDetalles().stream()
                .sorted(Comparator.comparing(DetalleCompraRequest::getInsumoId))
                .toList();

        // 1. Bloquear todos los insumos (FOR UPDATE) antes de modificar nada
        Map<Long, Insumo> insumosBloqueados = new HashMap<>();
        for (DetalleCompraRequest d : ordenados) {
            Insumo insumo = insumoRepository.findByIdForUpdate(d.getInsumoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Insumo no encontrado con id: " + d.getInsumoId()));
            insumosBloqueados.put(insumo.getId(), insumo);
        }

        // 2. Construir cabecera + detalles, actualizar stock y precio
        CompraInsumo compra = new CompraInsumo();
        compra.setProveedor(proveedor);
        compra.setUsuario(usuario);
        compra.setFechaCompra(request.getFechaCompra());

        BigDecimal total = BigDecimal.ZERO;
        for (DetalleCompraRequest d : ordenados) {
            Insumo insumo = insumosBloqueados.get(d.getInsumoId());
            BigDecimal cantidad = d.getCantidad().setScale(2, RoundingMode.HALF_UP);
            BigDecimal precio = d.getPrecioUnitario().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);

            DetalleCompra detalle = new DetalleCompra();
            detalle.setInsumo(insumo);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(precio);
            detalle.setSubtotal(subtotal);
            compra.agregarDetalle(detalle);

            // Incrementar stock y actualizar precio de referencia (RN-05: el costo de las
            // labores ya completadas no se recalcula, pero el precio vigente sí se actualiza).
            insumo.setStockActual(insumo.getStockActual().add(cantidad));
            insumo.setPrecioUnitario(precio);
            insumoRepository.save(insumo);

            total = total.add(subtotal);
        }
        compra.setTotalCompra(total.setScale(2, RoundingMode.HALF_UP));

        // 3. Guardar la compra con sus detalles (cascade) y asegurar id para movimientos
        CompraInsumo guardada = compraRepository.saveAndFlush(compra);

        // 4. Registrar un movimiento INGRESO_COMPRA por cada línea
        LocalDateTime ahora = LocalDateTime.now();
        for (DetalleCompra det : guardada.getDetalles()) {
            MovimientoStock mov = new MovimientoStock();
            mov.setInsumo(det.getInsumo());
            mov.setFecha(ahora);
            mov.setTipo(TipoMovimientoStock.INGRESO_COMPRA);
            mov.setCantidad(det.getCantidad());
            mov.setUsuario(usuario);
            mov.setCompraId(guardada.getId());
            mov.setObservacion("Compra a " + proveedor.getNombre() + " (" + proveedor.getCuit() + ")");
            movimientoStockRepository.save(mov);
        }

        return CompraResponse.from(guardada);
    }

    private void verificarCapacidadDecimal12(BigDecimal valor, String campo) {
        if (valor == null) {
            return;
        }
        BigDecimal absoluto = valor.abs();
        if (absoluto.compareTo(BigDecimal.ONE) >= 0) {
            int enteros = absoluto.setScale(0, RoundingMode.DOWN).toPlainString().length();
            if (enteros > ENTEROS_MAX_DECIMAL_12_2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        campo + " (" + valor.toPlainString() + ") excede la capacidad DECIMAL(12, 2) ("
                                + ENTEROS_MAX_DECIMAL_12_2 + " dígitos enteros)");
            }
        }
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado: " + username));
    }
}
