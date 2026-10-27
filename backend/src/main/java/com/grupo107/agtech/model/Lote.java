package com.grupo107.agtech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "lote")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "superficie_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal superficieHa;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal latitud;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal longitud;

    @Column(name = "poligono_wkt", columnDefinition = "TEXT")
    private String poligonoWkt;

    @Column(name = "tipo_suelo", length = 50)
    private String tipoSuelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoLote estado = EstadoLote.EN_PREPARACION;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
