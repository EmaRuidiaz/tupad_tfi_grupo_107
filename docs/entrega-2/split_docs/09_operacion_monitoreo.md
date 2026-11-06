# 9. Métricas Operativas, Monitoreo y Observabilidad

Este documento detalla cómo se medirá el funcionamiento del sistema una vez desplegado, dando soporte a **RNF-03 (Disponibilidad y Continuidad Operativa)** y **RNF-06 (Observabilidad)**. Se distinguen dos tipos de métricas: las **de negocio**, útiles para el productor, y las **técnicas**, útiles para el equipo de desarrollo, complementadas con el cálculo formal de disponibilidad (SLA) y la estrategia de respaldo y recuperación de datos (Backup & Disaster Recovery).

## 9.1. Métricas Operativas de Negocio

Se calculan a partir de los datos ya persistidos en el modelo, por lo que no requieren tablas adicionales. Se exponen en el Tablero (Módulo 9) y en el endpoint `/api/metricas`. Cuando una métrica se evalúa por campaña o ciclo productivo, aplica la ventana temporal `(fecha_cosecha_anterior, fecha_cosecha_actual]` formalizada en **RN-12** ([02_modelo_der.md](./02_modelo_der.md#22-formalización-de-campaña-agrícola--ciclo-productivo-y-política-de-cosechas)).

| Métrica | Cálculo | Fuente | Para qué sirve |
| :--- | :--- | :--- | :--- |
| Costo por hectárea | `SUM(costo_total_calculado) / superficie_ha` por lote y ciclo productivo (o rango de fechas) | `labor_agricola`, `lote` | Comparar eficiencia entre lotes y entre campañas sucesivas. |
| Rinde promedio | `AVG(rinde_ton_por_ha)` por cultivo y campaña | `registro_cosecha`, `cultivo` | Comparar campañas históricas y rendimiento por especie/variedad. |
| ROI y Margen Neto por lote | Ver Módulo 9 (doc 04) y RN-12 | `registro_cosecha`, `labor_agricola` | Rentabilidad final por ciclo productivo cerrado o acumulado multi-campaña. |
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
| Base de datos no disponible | Health check `DOWN`, errores 5xx. | Se revisa la instancia gestionada en el proveedor cloud; se restaura desde el último backup automático siguiendo el procedimiento de la sección 9.7 si hubo pérdida de datos. |
| Inconsistencia de stock | Tarea de control diario. | El `ADMIN` revisa el historial del insumo y corrige con un movimiento `AJUSTE` (RN-09). |
| Respuesta lenta del tablero | Percentil 95 > 2 s. | Se revisan los índices y los planes de ejecución de las consultas del tablero. |

## 9.6. Definición y Cálculo Formal de Disponibilidad del Sistema (SLA / RNF-03)

Para verificar objetivamente el cumplimiento del requerimiento no funcional **RNF-03** (disponibilidad mensual $\ge 99{,}0\%$), se establece la siguiente definición métrica y procedimiento de medición:

### 9.6.1. Fórmula de Disponibilidad Mensual

La disponibilidad se calcula sobre ventanas mensuales de 30 días calendario ($T_{\text{total}} = 30 \times 24 \times 60 = 43\,200\text{ minutos}$):

$$\text{Disponibilidad (\%)} = \left( \frac{T_{\text{operativo}}}{T_{\text{total}} - T_{\text{mantenimiento\_programado}}} \right) \times 100 = \left( 1 - \frac{T_{\text{indisponibilidad\_no\_planificada}}}{T_{\text{total}} - T_{\text{mantenimiento\_programado}}} \right) \times 100$$

Donde:
* **$T_{\text{total}}$:** Tiempo total del período de medición en minutos ($43\,200\text{ min}$ en un mes estándar de 30 días).
* **$T_{\text{mantenimiento\_programado}}$:** Minutos correspondientes a ventanas de mantenimiento preventivo notificadas con al menos 24 horas de anticipación y ejecutadas en horario de baja actividad agrícola (domingos de 02:00 a 04:00 ART).
* **$T_{\text{indisponibilidad\_no\_planificada}}$:** Suma de los intervalos en minutos en los que el servicio principal (API Backend + Base de Datos PostgreSQL) no puede procesar peticiones válidas.
* **Umbral máximo de indisponibilidad permitido ($\text{SLA} \ge 99{,}0\%$):** Para $T_{\text{mantenimiento\_programado}} = 0$, el tiempo máximo de caída no planificada admisible es de **$432\text{ minutos/mes}$** ($\approx 7{,}2\text{ horas/mes}$ o $\approx 1{,}68\text{ horas/semana}$).

### 9.6.2. Criterio Técnico de Medición (Health Probes)

1. **Sondeo sintético (Uptime Probe):** Un monitor externo realiza una petición `GET /actuator/health` cada **60 segundos** con un timeout de **5 segundos**.
2. **Condición de indisponibilidad (`DOWN`):** Un minuto se computa dentro de $T_{\text{indisponibilidad\_no\_planificada}}$ cuando:
   * El endpoint `/actuator/health` retorna código HTTP `503 Service Unavailable` (por ejemplo, desconexión de PostgreSQL reportada por `DataSourceHealthIndicator`) o no responde dentro de los 5 segundos en **2 sondeos consecutivos**, o bien
   * La tasa de errores HTTP `5xx` en los endpoints transaccionales (`/api/**`) supera el **10%** durante ese minuto.
3. **Aislamiento de la API Climática Externa:** De acuerdo con el diseño de resiliencia ([07_arquitectura_trazabilidad.md](./07_arquitectura_trazabilidad.md#73-resiliencia-ante-fallas-de-la-api-climática)), la caída del proveedor meteorológico externo activa el *circuit breaker* y la caché local (o la autorización manual RN-11), manteniendo el indicador global del sistema en estado operativo (`UP` con estado informativo `DEGRADED_WEATHER_API`). Por lo tanto, la indisponibilidad del proveedor climático externo **no interrumpe la disponibilidad del sistema** ni suma tiempo de caída al SLA propio.

## 9.7. Estrategia de Respaldo y Recuperación de Datos (Backup & Disaster Recovery)

Dado que el sistema resguarda información crítica de trazabilidad patrimonial y contable (stock valorizado de insumos, kardex de movimientos, costos de labores y márgenes de cosecha), se define la siguiente política de copias de seguridad y recuperación ante desastres sobre la base de datos PostgreSQL de producción:

### 9.7.1. Objetivos de Recuperación (RPO y RTO)

| Indicador | Objetivo comprometido | Justificación de negocio |
| :--- | :--- | :--- |
| **RPO (*Recovery Point Objective*)** | **$\le 24\text{ horas}$** (respaldo lógico diario) / **$\le 15\text{ minutos}$** (con archivado continuo de WAL del servicio cloud gestionado) | Pérdida máxima admisible de datos ante un siniestro grave del servidor primario. Cualquier operación del día en curso puede reconstruirse a partir de los remitos físicos de compra y partes diarios de campo. |
| **RTO (*Recovery Time Objective*)** | **$\le 2\text{ horas}$** | Tiempo máximo para aprovisionar una nueva instancia de base de datos, restaurar el último backup válido, verificar la integridad referencial y reconectar el backend Spring Boot. |

### 9.7.2. Esquema y Planificación de Backups

| Tipo de Respaldo | Herramienta / Mecanismo | Frecuencia y Horario | Retención | Destino y Seguridad |
| :--- | :--- | :--- | :--- | :--- |
| **Snapshot + WAL continuo (PITR)** | Servicio gestionado de PostgreSQL (Automated Snapshots + Write-Ahead Log archiving) | Continuo (WAL cada 5-15 min) + Snapshot diario | 7 días | Almacenamiento redundante del proveedor cloud con cifrado AES-256 en reposo. |
| **Backup Lógico Completo (Diario)** | `pg_dump --format=custom --no-owner --no-privileges` ejecutado vía tarea automatizada (`cron` / CI runner) | Diario a las **02:30 ART** (previo al control de consistencia de stock de las 03:00 ART) | **7 copias diarias** (rotación semanal) | Bucket de almacenamiento de objetos externo (S3-compatible) en región secundaria, transferido por TLS y cifrado en reposo. |
| **Backup Lógico Consolidado (Semanal / Mensual)** | Promoción del dump completo de los domingos | Semanal (domingos 02:30 ART) y mensual (día 1 de cada mes) | **4 copias semanales** y **6 copias mensuales** | Almacenamiento frío (* Cold Storage *) con bloqueo de eliminación accidental (*Object Lock* / solo lectura). |

### 9.7.3. Procedimiento de Restauración y Verificación Post-Recuperación

Ante una falla catastrófica o corrupción de datos en el entorno productivo (`prod`), el administrador ejecuta el siguiente protocolo estandarizado:

1. **Aislamiento y modo mantenimiento:** Se detiene temporalmente el contenedor/servicio del backend Spring Boot (o se activa el rechazo de tráfico de escritura en el proxy inverso) para evitar escrituras parciales durante la recuperación.
2. **Provisión y restauración:**
   * *Recuperación Point-in-Time (PITR):* Si se utiliza el servicio gestionado de PostgreSQL, se restaura la instancia al instante previo al incidente mediante los snapshots y segmentos WAL.
   * *Recuperación desde Dump Lógico:* Se crea una base de datos limpia (`agtech_prod_restored`) y se ejecuta:
     ```bash
     pg_restore --clean --if-exists --no-owner --dbname="$DATABASE_URL" backup_agtech_YYYYMMDD_0230.dump
     ```
3. **Verificación de integridad y consistencia contable:** Antes de reabrir el tráfico a los usuarios, se ejecutan tres controles obligatorios:
   * Verificación de que las 14 tablas del esquema y sus restricciones `CHECK` y claves foráneas estén activas.
   * Ejecución de las dos consultas de **control de consistencia de stock** documentadas en [03_diccionario_datos.md](./03_diccionario_datos.md#315-redundancias-controladas-y-criterios-de-recálculo), comprobando que `insumo.stock_actual` coincida exactamente con el saldo de `movimiento_stock` y que `insumo.stock_reservado` coincida con la suma de `labor_insumo` en estado `RESERVADO`.
   * Comprobación de que `GET /actuator/health` retorne HTTP `200 OK` con estado `"status": "UP"`.
4. **Reapertura del servicio y simulacros periódicos:** Una vez superadas las validaciones, se restablece el tráfico normal y se registra el incidente. Para garantizar que los backups sean efectivamente recuperables, se realiza una **prueba de restauración en entorno de staging una vez por trimestre**, midiendo que el tiempo total no supere el $\text{RTO} \le 2\text{ horas}$.
