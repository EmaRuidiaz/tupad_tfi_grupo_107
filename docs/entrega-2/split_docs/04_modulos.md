# 4. Listado de Módulos a Desarrollar

A continuación se detalla la descomposición funcional del software, incorporando los requerimientos ampliados de Seguridad, Compras y Notificaciones.

### Módulo 1: Seguridad y Autenticación de Usuarios (NUEVO)
* **Objetivo:** Garantizar el acceso controlado, trazabilidad y auditoría de todas las operaciones críticas.
* **Funcionalidades:**
  * Login mediante JWT y roles (ADMIN, AGRONOMO, OPERARIO).
  * Registro de auditoría (usuario que autoriza labores, ejecuta compras o realiza movimientos de stock).

### Módulo 2: Catastro y Gestión de Lotes (ABM) con Soporte GIS
* **Objetivo:** Administrar las parcelas agrícolas.
* **Funcionalidades:**
  * Almacenamiento de coordenadas centrales para clima.
  * Almacenamiento de Polígonos WKT (Well-Known Text) o integración con PostGIS para delimitar áreas, permitir futuros análisis espaciales y mapas de rendimiento.

### Módulo 3: Compras y Proveedores (NUEVO)
* **Objetivo:** Registrar las adquisiciones de insumos y actualizar costos.
* **Funcionalidades:**
  * ABM de Proveedores.
  * Registro de facturas/compras de insumos, que actualizan automáticamente `stock_actual` en la tabla `insumo`.
  * Generación de historial en `movimiento_stock` (Auditoría completa).

### Módulo 4: Inventario y Auditoría de Stock de Insumos
* **Objetivo:** Trazabilidad estricta del inventario.
* **Funcionalidades:**
  * Alerta de stock crítico (ver Módulo 8).
  * Movimientos de Stock detallados (`INGRESO_COMPRA`, `EGRESO_LABOR`, `AJUSTE`).

### Módulo 5: Parque de Maquinaria y Mantenimiento Predictivo
* **Objetivo:** Evitar downtime controlando horómetros.
* **Funcionalidades:**
  * Registro de servicios de mantenimiento (registrando usuario_id).

### Módulo 6: Asistente Inteligente Climático
* **Objetivo:** Validar labores agrícolas según variables meteorológicas (incluyendo humedad relativa, clave para pulverizaciones).

### Módulo 7: Gestión de Labores Agrícolas
* **Objetivo:** Control operativo y costeo.
* **Funcionalidades:**
  * Las labores ejecutadas persisten el campo `costo_total_calculado` en base al consumo de insumos (registrado en stock) y horas de maquinaria.

### Módulo 8: Sistema de Alertas y Notificaciones Transversales (NUEVO)
* **Objetivo:** Avisos proactivos al usuario para toma de decisiones.
* **Funcionalidades (Disparadores):**
  * **Stock Crítico:** Si `stock_actual <= stock_minimo_alerta`.
  * **Mantenimiento:** Si `horas_uso_actuales >= horas_para_proximo_service`.
  * **Clima adverso:** Avisos de reprogramación para labores en estado `BLOQUEADA_CLIMA`.
  * **Lotes listos:** Lotes que llevan demasiado tiempo en `LISTO_COSECHA`.

### Módulo 9: Tablero Financiero (ROI por Lote)
* **Objetivo:** Consolidar el resultado agronómico y económico final.
* **Fórmula de ROI:** 
  Se define formalmente el Retorno de Inversión (ROI) como:
  **`ROI (%) = (Margen Neto / Costos Totales Acumulados) * 100`**
  Donde:
  - `Margen Neto` = `Ingreso Bruto` - `Costos Totales Acumulados`
  - `Ingreso Bruto` = `toneladas_totales * precio_venta_por_ton`
  - `Costos Totales Acumulados` = Sumatoria de `costo_total_calculado` de todas las labores previas de ese lote.
* **Aclaración de Diseño:** Campos como `margen_neto_calculado` y `costo_total_calculado` están desnormalizados (persistidos) en las tablas transaccionales por cuestiones de performance e inmutabilidad histórica, en lugar de ser recalculados dinámicamente en cada consulta.

---
## 5. Justificación Tecnológica para la Entrega
* **PostgreSQL / H2:** Para soportar el modelo GIS de manera escalable y profesional a largo plazo, se proyecta el uso de PostgreSQL con PostGIS. Sin embargo, para mantener la compatibilidad simultánea exigida con la base de datos en memoria **H2** (para el perfil `dev` y testing local), la delimitación geográfica se almacena de forma abstracta mediante el tipo `TEXT` (formato WKT). Si la aplicación crece, se migrará definitivamente a tipos `GEOMETRY` nativos de PostGIS perdiendo la compatibilidad simple con H2.
