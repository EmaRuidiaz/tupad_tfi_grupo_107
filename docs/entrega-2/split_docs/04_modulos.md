# 4. Listado de Módulos a Desarrollar

A continuación se detalla la descomposición funcional del software, incorporando los requerimientos ampliados de Seguridad, Compras y Notificaciones.

### Módulo 1: Seguridad y Autenticación de Usuarios (NUEVO)
* **Objetivo:** Garantizar el acceso controlado, trazabilidad y auditoría de todas las operaciones críticas.
* **Funcionalidades:**
  * Login mediante JWT y roles (ADMIN, AGRONOMO, OPERARIO).
  * Registro de auditoría (usuario que autoriza labores, ejecuta compras o realiza movimientos de stock).
  * Autorización por rol según la matriz de permisos (ver doc 07, sección 7.2).

### Módulo 2: Catastro y Gestión de Lotes (ABM) con Soporte GIS
* **Objetivo:** Administrar las parcelas agrícolas y el ciclo de vida de cada campaña productiva.
* **Funcionalidades:**
  * Almacenamiento de coordenadas centrales para clima.
  * Almacenamiento de Polígonos WKT (Well-Known Text) o integración con PostGIS para delimitar áreas, permitir futuros análisis espaciales y mapas de rendimiento.
  * Gestión controlada de estados agronómicos del lote (`EN_PREPARACION` → `SEMBRADO` → `EN_CRECIMIENTO` → `LISTO_COSECHA` → `COSECHADO` / `DESCANSO`), delimitando el inicio y cierre de cada ciclo productivo (ver doc 02, sección 2.2).

### Módulo 3: Compras y Proveedores (NUEVO)
* **Objetivo:** Registrar las adquisiciones de insumos y actualizar costos.
* **Funcionalidades:**
  * ABM de Proveedores.
  * Registro de facturas/compras de insumos, que actualizan automáticamente `stock_actual` en la tabla `insumo`.
  * Generación de historial en `movimiento_stock` (Auditoría completa).

### Módulo 4: Inventario y Auditoría de Stock de Insumos
* **Objetivo:** Trazabilidad estricta del inventario y consistencia ante accesos concurrentes.
* **Funcionalidades:**
  * Alerta de stock crítico (ver Módulo 8).
  * Movimientos de Stock detallados (`INGRESO_COMPRA`, `EGRESO_LABOR`, `AJUSTE`).
  * Reserva lógica de stock para labores planificadas (`stock_reservado`), distinguiendo stock físico y stock disponible (ver doc 02, sección 2.1).
  * Control de concurrencia pesimista (`SELECT ... FOR UPDATE`) y bloqueo ordenado por `insumo_id` para evitar sobreasignación de insumos o *deadlocks* en operaciones simultáneas (ver doc 07, sección 7.4).

### Módulo 5: Parque de Maquinaria y Mantenimiento Predictivo
* **Objetivo:** Evitar downtime controlando horómetros.
* **Funcionalidades:**
  * Registro de servicios de mantenimiento (registrando usuario_id).

### Módulo 6: Asistente Inteligente Climático
* **Objetivo:** Validar labores agrícolas según variables meteorológicas (incluyendo humedad relativa, clave para pulverizaciones).
* **Funcionalidades:**
  * Comparación de las variables climáticas contra la `regla_climatica` del tipo de labor.
  * Tolerancia a fallos de la API externa: timeout, reintentos, circuit breaker, caché de 30 minutos y autorización manual con justificación (ver doc 07, sección 7.3).

### Módulo 7: Gestión de Labores Agrícolas
* **Objetivo:** Control operativo y costeo.
* **Funcionalidades:**
  * Las labores ejecutadas persisten el campo `costo_total_calculado` en base al consumo de insumos (registrado en stock) y horas de maquinaria (fórmula en doc 03, sección 3.15).
  * Ciclo de vida de la labor con transiciones controladas: programación con reserva de insumos, validación climática, ejecución, cierre, cancelación y reprogramación (ver doc 06, sección 6.3.2).

### Módulo 8: Sistema de Alertas y Notificaciones Transversales (NUEVO)
* **Objetivo:** Avisos proactivos al usuario para toma de decisiones.
* **Funcionalidades (Disparadores):**
  * **Stock Crítico:** Si `stock_actual <= stock_minimo_alerta`.
  * **Mantenimiento:** Si `horas_uso_actuales >= horas_para_proximo_service`.
  * **Clima adverso:** Avisos de reprogramación para labores en estado `BLOQUEADA_CLIMA`.
  * **Lotes listos:** Lotes que llevan más de 15 días en `LISTO_COSECHA`.

### Módulo 9: Tablero Financiero (ROI por Lote y Campaña)
* **Objetivo:** Consolidar el resultado agronómico y económico final por lote y ciclo productivo.
* **Fórmula de ROI:** 
  Se define formalmente el Retorno de Inversión (ROI) como:
  **`ROI (%) = (Margen Neto / Costos Totales Acumulados) * 100`**
  Donde:
  - `Margen Neto` = `Ingreso Bruto` - `Costos Totales Acumulados`
  - `Ingreso Bruto` = `toneladas_totales * precio_venta_por_ton`
  - `Costos Totales Acumulados` = Sumatoria de `costo_total_calculado` de las labores en estado `COMPLETADA` ejecutadas en ese lote durante el ciclo productivo correspondiente.
* **Alcance temporal y análisis histórico (Política de Cosechas, doc 02 §2.2):**
  - Al cerrar un ciclo productivo mediante un `registro_cosecha`, su `margen_neto_calculado` queda consolidado e inmutable para ese ciclo (`fecha_cosecha_anterior < fecha_ejecucion <= fecha_cosecha_actual`), permitiendo consultar y comparar múltiples campañas históricas consecutivas de un mismo lote.
  - Para lotes que cursan una campaña activa (sin cosecha registrada en el ciclo actual), el tablero exhibe los costos acumulados hasta el momento y el estado de ROI como `"Pendiente de cosecha"`.
* **Aclaración de Diseño:** Campos como `margen_neto_calculado` y `costo_total_calculado` están desnormalizados (persistidos) en las tablas transaccionales por cuestiones de performance (RNF-02) e inmutabilidad histórica (RN-05), en lugar de ser recalculados con precios vigentes en cada consulta.

---
## 5. Justificación Tecnológica para la Entrega
* **PostgreSQL / H2:** Para soportar el modelo GIS de manera escalable y profesional a largo plazo, se proyecta el uso de PostgreSQL con PostGIS. Sin embargo, para mantener la compatibilidad simultánea exigida con la base de datos en memoria **H2** (para el perfil `dev` y testing local), la delimitación geográfica se almacena de forma abstracta mediante el tipo `TEXT` (formato WKT). Si la aplicación crece, se migrará definitivamente a tipos `GEOMETRY` nativos de PostGIS perdiendo la compatibilidad simple con H2.
