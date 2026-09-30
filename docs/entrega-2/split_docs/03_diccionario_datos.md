# 3. Diccionario de Datos

Las siguientes tablas detallan los tipos de datos y restricciones formales (CHECK explícitos y UNIQUE) que aseguran la integridad del sistema.

### 3.1. Tabla `usuario`
Catálogo de usuarios del sistema para control de acceso y auditoría (Autenticación).

| Columna | Tipo de Dato | Nulo | Descripción / Restricciones |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | NO | Clave Primaria (Identity). |
| `username` | `VARCHAR(50)` | NO | `UNIQUE`. Nombre de usuario para login. |
| `password_hash` | `VARCHAR(255)` | NO | Hash de la contraseña. |
| `rol` | `VARCHAR(30)` | NO | `CHECK (rol IN ('ADMIN', 'AGRONOMO', 'OPERARIO'))`. |
| `email` | `VARCHAR(100)` | NO | `UNIQUE`. Correo electrónico para notificaciones. |

### 3.2. Tabla `lote`
Representa las parcelas productivas. Soporta coordenadas puntuales y polígonos GIS.

| Columna | Tipo de Dato | Nulo | Descripción / Restricciones |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | NO | Clave Primaria (Identity). |
| `nombre` | `VARCHAR(100)` | NO | `UNIQUE`. Nombre identificador del lote. |
| `superficie_ha` | `DECIMAL(10, 2)` | NO | `CHECK (superficie_ha > 0)`. Superficie en hectáreas. |
| `latitud`, `longitud` | `DECIMAL(10, 6)` | NO | Coordenadas para consulta climática puntual. |
| `poligono_wkt` | `TEXT` | SÍ | Geometría en WKT (Well-Known Text) para delimitación real GIS. |
| `estado` | `VARCHAR(30)` | NO | `CHECK` para estados del ciclo del cultivo. |
| `usuario_id` | `BIGINT` | SÍ | FK a `usuario`. Auditoría de quién dio de alta/administra. |

### 3.3. Tabla `insumo`
Catálogo de insumos agrícolas con stock actual consolidado.

| Columna | Tipo de Dato | Nulo | Descripción / Restricciones |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | NO | Clave Primaria (Identity). |
| `stock_actual` | `DECIMAL(12, 2)` | NO | `CHECK (stock_actual >= 0)`. Cantidad disponible. |
| `precio_unitario` | `DECIMAL(12, 2)` | NO | `CHECK (precio_unitario >= 0)`. Costo de adquisición actual. |
*(Atributos básicos omitidos por brevedad, consultar DER para más detalles)*

### 3.4. Tabla `movimiento_stock`
Historial completo de auditoría para ingresos y egresos de insumos.

| Columna | Tipo de Dato | Nulo | Descripción / Restricciones |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | NO | Clave Primaria. |
| `insumo_id` | `BIGINT` | NO | FK a `insumo`. |
| `fecha` | `TIMESTAMP` | NO | Fecha y hora del movimiento. |
| `tipo` | `VARCHAR(30)` | NO | `CHECK` en (`INGRESO_COMPRA`, `EGRESO_LABOR`, `AJUSTE`). |
| `cantidad` | `DECIMAL(12, 2)` | NO | Cantidad (positiva si es ingreso, negativa si es egreso). |
| `usuario_id` | `BIGINT` | NO | FK a `usuario` responsable. |
| `labor_id`, `compra_id`| `BIGINT` | SÍ | FK a la transacción de origen correspondiente. |

### 3.5. Tabla `maquinaria`
Flota de vehículos y maquinaria pesada agrícola.

| Columna | Tipo de Dato | Nulo | Descripción / Restricciones |
| :--- | :--- | :--- | :--- |
| `matricula_o_serie` | `VARCHAR(50)` | NO | `UNIQUE`. Número de serie o patente. |
| `horas_uso_actuales`| `DECIMAL(10, 2)` | NO | `CHECK (horas_uso_actuales >= 0)`. Horómetro actual. |

### 3.6. Tabla `labor_agricola`
Registro transaccional de labores planificadas y ejecutadas. Incorpora métricas climáticas.

| Columna | Tipo de Dato | Nulo | Descripción / Restricciones |
| :--- | :--- | :--- | :--- |
| `costo_total_calculado`| `DECIMAL(12,2)` | SÍ | Campo persistido/derivado que consolida el costo final. |
| `temperatura_registrada`| `DECIMAL(5,2)` | SÍ | Temp. del clima capturada. |
| `humedad_registrada_pct`| `DECIMAL(5,2)` | SÍ | Humedad capturada para validar `regla_climatica.humedad_min_pct`. |
| `usuario_id` | `BIGINT` | SÍ | Usuario que registró/supervisó la labor. |

### 3.7. Tabla `registro_cosecha` y `cultivo`
El cultivo es un catálogo aparte para evitar errores de tipeo o redundancia.

**Tabla `cultivo`**
* `nombre` `VARCHAR(60) UNIQUE`: "Soja 1°", "Soja 2°".
* `especie` `VARCHAR(60)`: "Soja", "Maíz".

**Tabla `registro_cosecha`**
* `cultivo_id` `BIGINT FK`: Enlace a catálogo de cultivo.
* `toneladas_totales` `DECIMAL(10, 2)` `CHECK (toneladas_totales >= 0)`.
* `rinde_ton_por_ha` `DECIMAL(8, 2)` `CHECK (rinde_ton_por_ha >= 0)`.

### 3.8. Tabla `compra_insumo` y `detalle_compra`
Módulo de adquisición a proveedores.

**Tabla `compra_insumo`**: `proveedor_id`, `usuario_id`, `fecha_compra`, `total_compra`.
**Tabla `detalle_compra`**: `compra_id`, `insumo_id`, `cantidad`, `precio_unitario`, `subtotal`.
