# 9. Métricas Operativas, Monitoreo y Observabilidad

Este documento detalla cómo se medirá el funcionamiento del sistema una vez desplegado, dando soporte a **RNF-03 (Disponibilidad)** y **RNF-06 (Observabilidad)**. Se distinguen dos tipos de métricas: las **de negocio**, útiles para el productor, y las **técnicas**, útiles para el equipo de desarrollo.

## 9.1. Métricas Operativas de Negocio

Se calculan a partir de los datos ya persistidos en el modelo, por lo que no requieren tablas adicionales. Se exponen en el Tablero (Módulo 9) y en el endpoint `/api/metricas`.

| Métrica | Cálculo | Fuente | Para qué sirve |
| :--- | :--- | :--- | :--- |
| Costo por hectárea | `SUM(costo_total_calculado) / superficie_ha` por lote y campaña | `labor_agricola`, `lote` | Comparar eficiencia entre lotes. |
| Rinde promedio | `AVG(rinde_ton_por_ha)` por cultivo | `registro_cosecha`, `cultivo` | Comparar campañas y cultivos. |
| ROI por lote | Ver Módulo 9 (doc 04) | `registro_cosecha`, `labor_agricola` | Rentabilidad final. |
| Tasa de bloqueo climático | Labores que pasaron por `BLOQUEADA_CLIMA` / labores con regla climática | `labor_agricola` | Medir el impacto del clima en la planificación. |
| Demora de ejecución | Promedio de `fecha_ejecucion - fecha_planificada` | `labor_agricola` | Detectar atrasos operativos. |
| Utilización de maquinaria | `SUM(horas_maquina_consumidas)` por máquina y mes | `labor_agricola`, `maquinaria` | Planificar la flota. |
| Costo de mantenimiento | `SUM(costo)` por máquina y tipo de mantenimiento | `registro_mantenimiento` | Detectar máquinas con roturas frecuentes (`CORRECTIVO_ROTURA`). |
| Insumos en stock crítico | Cantidad de insumos con `stock_actual <= stock_minimo_alerta` | `insumo` | Anticipar compras. |
| Stock comprometido | `stock_reservado / stock_actual` por insumo | `insumo` | Ver cuánto stock ya está asignado a labores. |

## 9.2. Métricas Técnicas

Se recolectan con **Spring Boot Actuator** y **Micrometer**, expuestas en `/actuator/metrics` (y opcionalmente en formato Prometheus en `/actuator/prometheus`).

| Métrica | Origen | Umbral de alerta propuesto |
| :--- | :--- | :--- |
| Estado de salud (`/actuator/health`) | Actuator: base de datos, espacio en disco y API climática. | Estado distinto de `UP` durante más de 2 minutos. |
| Tiempo de respuesta por endpoint (`http.server.requests`) | Micrometer | Percentil 95 del tablero > 2 s (RNF-02). |
| Tasa de errores HTTP 5xx | Micrometer | > 5% de las peticiones en 5 minutos. |
| Estado del circuit breaker de clima | Resilience4j (`resilience4j.circuitbreaker.state`) | Circuito `OPEN`. |
| Llamadas fallidas a la API climática | Resilience4j | > 20% en 10 minutos. |
| Aciertos de caché de clima | Caffeine (`cache.gets`) | Informativa: mide el ahorro de llamadas a la API. |
| Pool de conexiones a la BD | HikariCP (`hikaricp.connections.pending`) | Conexiones pendientes > 0 durante más de 1 minuto. |
| Intentos de login fallidos | Contador propio (`auth.login.fallido`) | > 10 por usuario en 5 minutos (posible ataque de fuerza bruta). |

**Seguridad:** sólo `/actuator/health` es público (sin detalles). El resto de los endpoints de Actuator requiere rol `ADMIN`.

## 9.3. Registro de Eventos (Logging)

* **Formato:** logs estructurados en JSON (Logback + `logstash-logback-encoder`) para poder filtrarlos por campo.
* **Identificador de petición:** un filtro HTTP genera un `requestId` por petición y lo agrega al MDC, de modo que todos los logs de una misma operación puedan seguirse juntos. El `requestId` se devuelve en la cabecera `X-Request-Id`.
* **Niveles:**
  * `ERROR`: excepciones no controladas y fallas de la base de datos.
  * `WARN`: aperturas del circuit breaker, uso de datos de caché, autorizaciones manuales (RN-11), inconsistencias de stock detectadas.
  * `INFO`: cambios de estado de labores, lotes y maquinaria; compras registradas.
* **Datos sensibles:** nunca se registran contraseñas, hashes ni tokens JWT completos.
* **Separación de la auditoría:** los logs son para diagnóstico técnico. La auditoría de negocio (quién hizo qué) se garantiza en la base de datos mediante los campos `usuario_id` (RN-04), que no dependen de la retención de logs.

## 9.4. Tareas Programadas de Control

| Tarea | Frecuencia | Acción |
| :--- | :--- | :--- |
| Evaluación de alertas | Cada 15 minutos | Genera alertas de stock crítico, mantenimiento y cosecha demorada (RF-07). |
| Vencimiento de aprobación climática | Cada 5 minutos | Devuelve a `PLANIFICADA` las labores en `APROBADA_CLIMA` con más de 2 horas (doc 06, sección 6.3.2). |
| Control de consistencia de stock | Diario (03:00) | Compara `stock_actual` con la suma de `movimiento_stock` y `stock_reservado` con las reservas activas. Si hay diferencias, registra un `WARN` y genera una alerta para el `ADMIN` (doc 03, sección 3.15). |

## 9.5. Procedimiento ante Incidentes

| Situación | Detección | Respuesta |
| :--- | :--- | :--- |
| Caída de la API climática | Circuit breaker `OPEN` / health check degradado. | El sistema sigue operando con caché o autorización manual (doc 07, sección 7.3). Se verifica el estado del proveedor y, si la caída se prolonga, se evalúa cambiar de proveedor implementando otra clase de `ProveedorClima`. |
| Base de datos no disponible | Health check `DOWN`, errores 5xx. | Se revisa la instancia gestionada en el proveedor cloud; se restaura desde el último backup automático si hubo pérdida de datos. |
| Inconsistencia de stock | Tarea de control diario. | El `ADMIN` revisa el historial del insumo y corrige con un movimiento `AJUSTE` (RN-09). |
| Respuesta lenta del tablero | Percentil 95 > 2 s. | Se revisan los índices y los planes de ejecución de las consultas del tablero. |
