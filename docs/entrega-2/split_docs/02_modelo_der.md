# 2. Diagrama Entidad-Relación (DER)

A continuación se presenta el modelo relacional diseñado para la plataforma. El núcleo del sistema gira en torno a la entidad **`labor_agricola`**, que articula el lote, la maquinaria asignada, los insumos consumidos y las condiciones climáticas validadas. Se han incorporado las entidades de **Usuario**, **Compras** y trazabilidad de **Movimientos de Stock**, así como **Cultivo** como catálogo independiente.

```mermaid
erDiagram
    USUARIO ||--o{ LOTE : "registra/supervisa"
    USUARIO ||--o{ LABOR_AGRICOLA : "registra"
    USUARIO ||--o{ REGISTRO_MANTENIMIENTO : "aprueba"
    USUARIO ||--o{ COMPRA_INSUMO : "realiza"
    USUARIO ||--o{ MOVIMIENTO_STOCK : "ejecuta"
    USUARIO ||--o{ REGISTRO_COSECHA : "declara"

    LOTE ||--o{ LABOR_AGRICOLA : "se realiza en"
    LOTE ||--o{ REGISTRO_COSECHA : "produce"
    
    MAQUINARIA ||--o{ LABOR_AGRICOLA : "ejecuta"
    MAQUINARIA ||--o{ REGISTRO_MANTENIMIENTO : "recibe"
    
    LABOR_AGRICOLA ||--o{ LABOR_INSUMO : "consume"
    LABOR_AGRICOLA ||--o| MOVIMIENTO_STOCK : "genera egreso en"
    LABOR_AGRICOLA ||--o| REGISTRO_COSECHA : "vincula a"
    
    INSUMO ||--o{ LABOR_INSUMO : "es utilizado en"
    INSUMO ||--o{ DETALLE_COMPRA : "es adquirido en"
    INSUMO ||--o{ MOVIMIENTO_STOCK : "registra historial en"
    
    PROVEEDOR ||--o{ COMPRA_INSUMO : "abastece"
    COMPRA_INSUMO ||--o{ DETALLE_COMPRA : "contiene"
    COMPRA_INSUMO ||--o{ MOVIMIENTO_STOCK : "genera ingreso en"
    
    CULTIVO ||--o{ REGISTRO_COSECHA : "es el tipo de"
    
    REGLA_CLIMATICA ||--o{ LABOR_AGRICOLA : "valida condiciones de"

    USUARIO {
        BIGINT id PK
        VARCHAR username
        VARCHAR password_hash
        VARCHAR rol
        VARCHAR email
    }
    
    PROVEEDOR {
        BIGINT id PK
        VARCHAR nombre
        VARCHAR cuit
        VARCHAR telefono
        VARCHAR email
    }
    
    CULTIVO {
        BIGINT id PK
        VARCHAR nombre
        VARCHAR especie
    }
    
    COMPRA_INSUMO {
        BIGINT id PK
        BIGINT proveedor_id FK
        BIGINT usuario_id FK
        TIMESTAMP fecha_compra
        DECIMAL total_compra
    }
    
    DETALLE_COMPRA {
        BIGINT id PK
        BIGINT compra_id FK
        BIGINT insumo_id FK
        DECIMAL cantidad
        DECIMAL precio_unitario
        DECIMAL subtotal
    }
    
    MOVIMIENTO_STOCK {
        BIGINT id PK
        BIGINT insumo_id FK
        TIMESTAMP fecha
        VARCHAR tipo
        DECIMAL cantidad
        BIGINT usuario_id FK
        BIGINT labor_id FK
        BIGINT compra_id FK
        TEXT observacion
    }

    LOTE {
        BIGINT id PK
        VARCHAR nombre
        DECIMAL superficie_ha
        DECIMAL latitud
        DECIMAL longitud
        TEXT poligono_wkt
        VARCHAR tipo_suelo
        VARCHAR estado
        TEXT observaciones
        BIGINT usuario_id FK
    }

    INSUMO {
        BIGINT id PK
        VARCHAR nombre
        VARCHAR categoria
        VARCHAR unidad_medida
        DECIMAL stock_actual
        DECIMAL stock_reservado
        DECIMAL stock_minimo_alerta
        DECIMAL precio_unitario
    }

    MAQUINARIA {
        BIGINT id PK
        VARCHAR nombre
        VARCHAR tipo
        VARCHAR matricula_o_serie
        DECIMAL horas_uso_actuales
        DECIMAL horas_para_proximo_service
        DECIMAL consumo_litros_hora
        DECIMAL costo_operativo_hora
        VARCHAR estado
    }

    REGISTRO_MANTENIMIENTO {
        BIGINT id PK
        BIGINT maquinaria_id FK
        BIGINT usuario_id FK
        DATE fecha_service
        DECIMAL horas_maquina_en_service
        VARCHAR tipo_mantenimiento
        DECIMAL costo
        DECIMAL proximo_service_horas
        TEXT descripcion
    }

    LABOR_AGRICOLA {
        BIGINT id PK
        BIGINT lote_id FK
        BIGINT maquinaria_id FK
        BIGINT usuario_id FK
        VARCHAR tipo_labor
        TIMESTAMP fecha_planificada
        TIMESTAMP fecha_ejecucion
        VARCHAR estado
        DECIMAL horas_maquina_consumidas
        DECIMAL costo_total_calculado
        DECIMAL temperatura_registrada
        DECIMAL viento_velocidad_kmh
        DECIMAL probabilidad_lluvia_pct
        DECIMAL humedad_registrada_pct
        TEXT observaciones_climaticas
    }

    LABOR_INSUMO {
        BIGINT id PK
        BIGINT labor_id FK
        BIGINT insumo_id FK
        DECIMAL cantidad_utilizada
        DECIMAL costo_subtotal
        VARCHAR estado_reserva
    }

    REGISTRO_COSECHA {
        BIGINT id PK
        BIGINT lote_id FK
        BIGINT labor_id FK
        BIGINT cultivo_id FK
        BIGINT usuario_id FK
        DATE fecha_cosecha
        DECIMAL toneladas_totales
        DECIMAL rinde_ton_por_ha
        DECIMAL precio_venta_por_ton
        DECIMAL ingreso_bruto_total
        DECIMAL margen_neto_calculado
    }

    REGLA_CLIMATICA {
        BIGINT id PK
        VARCHAR tipo_labor
        DECIMAL viento_max_kmh
        DECIMAL viento_min_kmh
        DECIMAL temp_min_c
        DECIMAL temp_max_c
        DECIMAL prob_lluvia_max_pct
        DECIMAL humedad_min_pct
        VARCHAR mensaje_alerta
    }
```

### 2.1. Reserva lógica de stock

Para dar soporte al caso de uso **CU-03 (Programar Labor Agrícola)**, el modelo incorpora la **reserva de stock** sin necesidad de una tabla adicional:

* **`insumo.stock_reservado`**: cantidad del insumo comprometida por labores planificadas que todavía no se ejecutaron. El stock que puede asignarse a una nueva labor es el **stock disponible**: `stock_actual - stock_reservado`.
* **`labor_insumo.estado_reserva`**: indica en qué situación se encuentra cada línea de insumo de una labor:
  * `RESERVADO`: la labor está planificada y la cantidad está comprometida (suma en `stock_reservado`).
  * `CONSUMIDO`: la labor se inició, se descontó el stock físico y se registró un `EGRESO_LABOR` en `movimiento_stock`.
  * `LIBERADO`: la labor se canceló y la cantidad volvió a estar disponible.

La reserva no genera registros en `movimiento_stock` porque no altera el stock físico; sólo el consumo real (`EGRESO_LABOR`) queda en el historial de movimientos. Las transiciones completas se documentan en [06_reglas_negocio_casos_uso.md](./06_reglas_negocio_casos_uso.md#63-estados-de-negocio-y-transiciones).
