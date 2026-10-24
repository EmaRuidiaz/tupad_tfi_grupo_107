package com.grupo107.agtech.service;

import com.grupo107.agtech.controller.dto.AjusteStockRequest;
import com.grupo107.agtech.controller.dto.InsumoRequest;
import com.grupo107.agtech.controller.dto.InsumoResponse;
import com.grupo107.agtech.controller.dto.MovimientoStockResponse;
import com.grupo107.agtech.model.Insumo;
import com.grupo107.agtech.model.MovimientoStock;
import com.grupo107.agtech.model.TipoMovimientoStock;
import com.grupo107.agtech.model.Usuario;
import com.grupo107.agtech.repository.InsumoRepository;
import com.grupo107.agtech.repository.MovimientoStockRepository;
import com.grupo107.agtech.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InsumoService {

    private final InsumoRepository insumoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioRepository usuarioRepository;

    public InsumoService(InsumoRepository insumoRepository,
                         MovimientoStockRepository movimientoStockRepository,
                         UsuarioRepository usuarioRepository) {
        this.insumoRepository = insumoRepository;
        this.movimientoStockRepository = movimientoStockRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<InsumoResponse> listarTodos(Boolean soloStockCritico) {
        return insumoRepository.findAllByOrderByIdAsc().stream()
                .filter(insumo -> !Boolean.TRUE.equals(soloStockCritico) || insumo.isStockCritico())
                .map(InsumoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InsumoResponse obtenerPorId(Long id) {
        return InsumoResponse.from(buscarInsumo(id));
    }

    @Transactional
    public InsumoResponse crear(InsumoRequest request, String username) {
        BigDecimal stockInicial = request.getStockInicial() != null
                ? request.getStockInicial()
                : BigDecimal.ZERO;

        Insumo insumo = new Insumo();
        insumo.setNombre(request.getNombre().trim());
        insumo.setCategoria(request.getCategoria());
        insumo.setUnidadMedida(request.getUnidadMedida());
        insumo.setStockActual(stockInicial);
        insumo.setStockReservado(BigDecimal.ZERO);
        insumo.setStockMinimoAlerta(request.getStockMinimoAlerta());
        insumo.setPrecioUnitario(request.getPrecioUnitario());

        Insumo guardado = insumoRepository.save(insumo);

        // Regla doc 03 (sección 3.15): el stock inicial se registra como un AJUSTE ("Stock inicial").
        if (stockInicial.compareTo(BigDecimal.ZERO) > 0) {
            Usuario usuario = buscarUsuario(username);
            MovimientoStock movInicial = new MovimientoStock();
            movInicial.setInsumo(guardado);
            movInicial.setFecha(LocalDateTime.now());
            movInicial.setTipo(TipoMovimientoStock.AJUSTE);
            movInicial.setCantidad(stockInicial);
            movInicial.setUsuario(usuario);
            movInicial.setObservacion("Stock inicial");
            movimientoStockRepository.save(movInicial);
        }

        return InsumoResponse.from(guardado);
    }

    @Transactional
    public InsumoResponse actualizar(Long id, InsumoRequest request) {
        Insumo insumo = buscarInsumo(id);
        insumo.setNombre(request.getNombre().trim());
        insumo.setCategoria(request.getCategoria());
        insumo.setUnidadMedida(request.getUnidadMedida());
        insumo.setStockMinimoAlerta(request.getStockMinimoAlerta());
        insumo.setPrecioUnitario(request.getPrecioUnitario());

        return InsumoResponse.from(insumoRepository.save(insumo));
    }

    @Transactional
    public void eliminar(Long id) {
        Insumo insumo = buscarInsumo(id);
        if (insumo.getStockActual().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el insumo porque todavía tiene stock (" + insumo.getStockActual()
                            + " " + insumo.getUnidadMedida() + "). Registrá un ajuste que lo lleve a cero antes de eliminarlo");
        }
        if (movimientoStockRepository.existsByInsumoId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el insumo porque registra movimientos de stock, compras o labores asociadas");
        }
        try {
            insumoRepository.delete(insumo);
            insumoRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el insumo porque registra movimientos de stock, compras o labores asociadas");
        }
    }

    @Transactional
    public MovimientoStockResponse registrarAjuste(Long id, AjusteStockRequest request, String username) {
        Insumo insumo = buscarInsumo(id);

        if (request.getCantidad().compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad del ajuste debe ser distinta de cero");
        }

        BigDecimal nuevoStock = insumo.getStockActual().add(request.getCantidad());
        if (nuevoStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El ajuste dejaría el stock actual en negativo (" + nuevoStock + ")");
        }
        if (nuevoStock.compareTo(insumo.getStockReservado()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El ajuste dejaría el stock actual (" + nuevoStock
                            + ") por debajo del stock reservado (" + insumo.getStockReservado() + ")");
        }

        Usuario usuario = buscarUsuario(username);

        insumo.setStockActual(nuevoStock);
        insumoRepository.save(insumo);

        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setInsumo(insumo);
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setTipo(TipoMovimientoStock.AJUSTE);
        movimiento.setCantidad(request.getCantidad());
        movimiento.setUsuario(usuario);
        movimiento.setObservacion(request.getObservacion().trim());

        MovimientoStock guardado = movimientoStockRepository.save(movimiento);
        return MovimientoStockResponse.from(guardado);
    }

    @Transactional(readOnly = true)
    public List<MovimientoStockResponse> listarMovimientos(Long id) {
        buscarInsumo(id);
        return movimientoStockRepository.findByInsumoIdOrderByFechaDescIdDesc(id).stream()
                .map(MovimientoStockResponse::from)
                .toList();
    }

    private Insumo buscarInsumo(Long id) {
        return insumoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Insumo no encontrado con id: " + id));
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado: " + username));
    }
}
