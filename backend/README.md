# Backend - Sistema AgTech (Spring Boot)

API REST en Java 17 + Spring Boot 4.1 (Spring Web, Spring Data JPA, Actuator).

## Ejecutar en desarrollo (perfil `dev`, H2 en memoria)

```bash
cd backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Al iniciar, H2 carga automáticamente [`database/schema.sql`](../database/schema.sql) y [`database/data.sql`](../database/data.sql) (el `pom.xml` los copia al classpath, por lo que **no hay que duplicarlos**). Hibernate no genera el esquema (`ddl-auto: none`).

| URL | Descripción |
| :--- | :--- |
| `http://localhost:8080/api/ping` | Verificación rápida del backend |
| `http://localhost:8080/actuator/health` | Health check (público, sin detalles) |
| `http://localhost:8080/h2-console` | Consola H2 (JDBC URL: `jdbc:h2:mem:agtechdb;MODE=PostgreSQL`, user `sa`, pass `password`) |

## Tests

```bash
./mvnw test
```

## Producción (perfil `prod`, PostgreSQL)

```bash
SPRING_PROFILES_ACTIVE=prod DB_HOST=... DB_NAME=agtechdb DB_USER=... DB_PASSWORD=... JWT_SECRET=... ./mvnw spring-boot:run
```

* Las credenciales **no tienen valor por defecto** y deben venir de variables de entorno.
* `schema.sql` contiene `DROP TABLE`, por eso en `prod` la carga automática está deshabilitada (`spring.sql.init.mode: never`). Aplicarlo **una sola vez** de forma manual (`psql -f database/schema.sql`) y luego, opcionalmente, `data.sql`.
* Hibernate valida el esquema contra las entidades (`ddl-auto: validate`).

## Autenticación y roles (HU-14)

Todos los endpoints requieren un token JWT salvo `POST /api/auth/login`, `/api/ping` y `/actuator/health` (y la consola H2, sólo en `dev`).

**Usuarios semilla** (`database/data.sql`, contraseña de desarrollo `agtech123` para los tres):

| Usuario | Rol |
| :--- | :--- |
| `admin` | `ADMIN` |
| `agronomo1` | `AGRONOMO` |
| `operario1` | `OPERARIO` |

```bash
# 1. Login: devuelve el token, los roles y el instante de vencimiento (8 horas)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"agtech123"}'

# 2. Usar el token en cada request
curl http://localhost:8080/api/... -H "Authorization: Bearer <token>"
```

| Situación | Respuesta |
| :--- | :--- |
| Credenciales incorrectas | `401` (`Usuario o contraseña incorrectos`) |
| Body de login incompleto | `400` |
| Sin token, token inválido, manipulado o vencido | `401` |
| Rol sin permiso para el endpoint (`@PreAuthorize`) | `403` |

Los errores de seguridad se devuelven en JSON: `{"status":401,"error":"Unauthorized","message":"..."}`.

## Inventario de insumos (HU-04)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/insumos` | Todos | Lista el catálogo. `?stockCritico=true` devuelve sólo los insumos con `stock_actual <= stock_minimo_alerta` |
| `GET` | `/api/insumos/{id}` | Todos | Detalle de un insumo |
| `GET` | `/api/insumos/{id}/movimientos` | Todos | Historial de movimientos de stock (más reciente primero) |
| `POST` | `/api/insumos` | `ADMIN` | Alta. Si `stockInicial > 0` se registra un `AJUSTE` "Stock inicial" |
| `PUT` | `/api/insumos/{id}` | `ADMIN` | Modifica datos de catálogo, umbral y precio. **No** modifica el stock |
| `DELETE` | `/api/insumos/{id}` | `ADMIN` | Baja. Rechazada (`409`) si el insumo tiene stock o movimientos |
| `POST` | `/api/insumos/{id}/ajustes` | `ADMIN` | Ajuste manual (`cantidad` positiva o negativa + `observacion` obligatoria) |

Cada respuesta de insumo incluye los campos derivados `stockDisponible` (`stockActual - stockReservado`) y `stockCritico`.

**Reglas que aplica el backend:**
* El stock físico sólo cambia mediante movimientos, que quedan asociados al usuario del token (RN-04). Los movimientos no se modifican ni se borran (RN-09).
* Un ajuste no puede dejar el stock negativo ni por debajo del stock reservado por labores.
* Cantidades e importes: hasta 10 enteros y 2 decimales (`DECIMAL(12, 2)`); fuera de rango responde `400`.
* Los datos semilla cumplen `stock_actual = SUM(movimiento_stock.cantidad)` para cada insumo (verificado por test).

```bash
# Ajuste de stock (requiere token de admin)
curl -X POST http://localhost:8080/api/insumos/5/ajustes \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"cantidad": 50, "observacion": "Recuento físico de depósito"}'
```

| Situación | Respuesta |
| :--- | :--- |
| Validación de campos (obligatorios, negativos, precisión) o ajuste inválido | `400` |
| Insumo inexistente | `404` |
| Baja de un insumo con stock, movimientos o labores asociadas | `409` |

## Gestión de lotes (HU-03)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/lotes` | Todos | Lista los lotes. `?estado=SEMBRADO` filtra por estado agronómico |
| `GET` | `/api/lotes/{id}` | Todos | Detalle de un lote |
| `POST` | `/api/lotes` | `ADMIN`, `AGRONOMO` | Alta. El lote queda en `EN_PREPARACION` y asociado al usuario del token |
| `PUT` | `/api/lotes/{id}` | `ADMIN`, `AGRONOMO` | Modifica nombre, superficie, coordenadas, polígono, suelo y observaciones. **No** modifica el estado |
| `DELETE` | `/api/lotes/{id}` | `ADMIN`, `AGRONOMO` | Baja. Rechazada (`409`) si el lote tiene labores o cosechas |
| `PATCH` | `/api/lotes/{id}/estado` | `ADMIN`, `AGRONOMO` | Cambio de estado agronómico (`estado` + `observacion` opcional) |

Cada respuesta de lote incluye `transicionesPermitidas`: los estados a los que se puede pasar manualmente desde el estado actual.

**Reglas que aplica el backend:**
* Nombre único (sin distinguir mayúsculas), superficie mayor a 0, latitud entre -90 y 90 y longitud entre -180 y 180 (RF-02).
* `poligonoWkt` es opcional; si se envía debe ser `POLYGON((lon lat, lon lat, ...))` con anillos cerrados de al menos 4 puntos.
* Transiciones de estado según doc 06, sección 6.3.1 (RN-08). `EN_PREPARACION -> SEMBRADO` y `LISTO_COSECHA -> COSECHADO` no se pueden hacer a mano: las realiza el sistema al completar una siembra o registrar una cosecha.
* Volver a `EN_PREPARACION` desde `SEMBRADO` o `EN_CRECIMIENTO` es una pérdida total y requiere `observacion`. Toda observación del cambio se agrega a las observaciones del lote con fecha.

```bash
# Cambio de estado (requiere token de admin o agrónomo)
curl -X PATCH http://localhost:8080/api/lotes/1/estado \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"estado": "EN_CRECIMIENTO"}'
```

| Situación | Respuesta |
| :--- | :--- |
| Validación de campos, nombre duplicado, polígono WKT inválido o pérdida total sin observación | `400` |
| Lote inexistente | `404` |
| Baja de un lote con labores o cosechas, o transición de estado no permitida | `409` |

## Parque de maquinaria (HU-05)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/maquinarias` | Todos | Lista todas las maquinarias ordenadas por `id`. Admite filtros opcionales: `?estado=OPERATIVO` y `?mantenimientoVencido=true` |
| `GET` | `/api/maquinarias/{id}` | Todos | Detalle de una maquinaria |
| `POST` | `/api/maquinarias` | `ADMIN` | Alta de maquinaria (inicia en estado `OPERATIVO`; `horasParaProximoService` debe ser mayor a las horas iniciales) |
| `PUT` | `/api/maquinarias/{id}` | `ADMIN` | Modifica datos, horómetro, umbral de service, consumo y costo. Protegido si está `EN_LABOR` y el horómetro no puede reducirse |
| `PATCH` | `/api/maquinarias/{id}/estado` | Todos (según transición) | Cambio de estado controlado (`OPERATIVO` ↔ `EN_MANTENIMIENTO`, `FUERA_DE_SERVICIO`). Bloquea regreso a `OPERATIVO` si el service está vencido |
| `DELETE` | `/api/maquinarias/{id}` | `ADMIN` | Baja física. Rechazada (`409`) si está `EN_LABOR` o registra labores/mantenimientos |

Cada respuesta incluye los campos derivados `horasRestantesService` (`horasParaProximoService - horasUsoActuales`) y `mantenimientoVencido` (`horasUsoActuales >= horasParaProximoService`, RN-06).

**Reglas que aplica el backend:**
* `matriculaOSerie` es única (sin distinguir mayúsculas/minúsculas). En el alta se exige `horasParaProximoService > horasUsoActuales`. En `PUT`, el horómetro no puede reducirse por debajo del actual ni del último service registrado, y `horasParaProximoService` debe ser mayor al último service registrado.
* Transiciones de estado (RN-08): cualquier rol puede ingresar una máquina `OPERATIVO` a `EN_MANTENIMIENTO`; sólo `ADMIN` puede dar de baja lógica (`FUERA_DE_SERVICIO`), reingresar de `FUERA_DE_SERVICIO` a `EN_MANTENIMIENTO` o volver manualmente de `EN_MANTENIMIENTO` a `OPERATIVO` sin registrar service. El estado `EN_LABOR` es exclusivo del ciclo de ejecución de labores (RN-10).
* Si la maquinaria está en ejecución (`EN_LABOR`) no se puede modificar ni eliminar (`409 Conflict`). Si tiene historial de labores o mantenimientos asociados, se bloquea su eliminación física (`409 Conflict`) sugiriendo su paso a `FUERA_DE_SERVICIO`.

| Situación | Respuesta |
| :--- | :--- |
| Validación de campos, matrícula duplicada, reducción de horómetro o umbral inválido | `400` |
| Cambio de estado reservado al administrador | `403` |
| Maquinaria inexistente | `404` |
| Transición de estado no permitida, edición/baja en labor o baja con historial asociado | `409` |

## Mantenimientos de maquinaria (HU-06)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/maquinarias/{id}/mantenimientos` | Todos | Historial de services de la máquina, del más reciente al más antiguo |
| `POST` | `/api/maquinarias/{id}/mantenimientos` | `ADMIN`, `AGRONOMO`, `OPERARIO` | Registra un service (CU-06), asociado al usuario del token |

**Reglas que aplica el backend:**
* Al registrar el service, `proximoServiceHoras` se copia a `maquinaria.horasParaProximoService` y la máquina queda `OPERATIVO` (si estaba `EN_MANTENIMIENTO`, vuelve sola: RF-04).
* No se puede registrar un service si la máquina está `EN_LABOR` ni `FUERA_DE_SERVICIO` (un administrador debe reingresarla primero a `EN_MANTENIMIENTO`).
* La lectura del horómetro (`horasMaquinaEnService`) no puede ser menor a la del último service, y la fecha no puede ser futura ni anterior a la del último service. Si la lectura supera el horómetro de la máquina, se actualiza `horasUsoActuales`.
* `proximoServiceHoras` debe ser mayor a la lectura y al horómetro actual, para que la máquina no quede con el service vencido.

```bash
# Registrar un service (cualquier rol autenticado)
curl -X POST http://localhost:8080/api/maquinarias/1/mantenimientos \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"fechaService": "2026-10-27", "horasMaquinaEnService": 990, "tipoMantenimiento": "PREVENTIVO_PROGRAMADO", "costo": 450, "proximoServiceHoras": 1250, "descripcion": "Cambio de aceite y filtros"}'
```

`tipoMantenimiento` admite `PREVENTIVO_PROGRAMADO`, `CAMBIO_ACEITE_FILTROS` y `CORRECTIVO_ROTURA`.

| Situación | Respuesta |
| :--- | :--- |
| Validación de campos, fecha futura, lectura o fecha anterior al último service, o próximo service inválido | `400` |
| Maquinaria inexistente | `404` |
| Máquina `EN_LABOR` o `FUERA_DE_SERVICIO` | `409` |

## Reglas climáticas agronómicas (HU-07)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/reglas-climaticas` | Todos | Lista las reglas climáticas ordenadas por `id`. Admite `?tipoLabor=PULVERIZACION` |
| `GET` | `/api/reglas-climaticas/{id}` | Todos | Detalle de una regla climática |
| `POST` | `/api/reglas-climaticas` | `ADMIN` | Alta de regla para un tipo de labor sin regla configurada |
| `PUT` | `/api/reglas-climaticas/{id}` | `ADMIN` | Modifica umbrales de viento, temperatura, lluvia, humedad y mensaje de alerta |
| `DELETE` | `/api/reglas-climaticas/{id}` | `ADMIN` | Elimina una regla climática (rechazada con `409` si existen labores pendientes de ese tipo) |
| `POST` | `/api/reglas-climaticas/{id}/evaluar` | Todos | Evalúa variables meteorológicas contra la regla e informa si aprueba (`APROBADA_CLIMA`) o bloquea (`BLOQUEADA_CLIMA`) con el detalle de incumplimientos |

**Reglas que aplica el backend (doc 03, sección 3.8 y RN-01):**
* Existe a lo sumo una regla por `tipoLabor` (`PULVERIZACION`, `SIEMBRA`, `COSECHA`, `FERTILIZACION`). Las labores de `LABRANZA` no llevan regla climática asociada y se rechazan con `400`.
* Se valida `vientoMinKmh <= vientoMaxKmh`, `tempMinC < tempMaxC`, porcentajes (`probLluviaMaxPct`, `humedadMinPct`) entre `0` y `100` y precisión `DECIMAL(5, 2)`.
* Si existen labores agrícolas en estado `PLANIFICADA`, `APROBADA_CLIMA` o `BLOQUEADA_CLIMA` para ese `tipoLabor`, se impide eliminar la regla o cambiar su tipo (`409 Conflict`).

| Situación | Respuesta |
| :--- | :--- |
| Umbrales inválidos (`vientoMin > vientoMax`, `tempMin >= tempMax`), porcentajes fuera de `0..100`, `LABRANZA` o `tipoLabor` duplicado | `400` |
| Modificación por un usuario no `ADMIN` | `403` |
| Regla climática inexistente | `404` |
| Baja o cambio de `tipoLabor` con labores pendientes asociadas | `409` |

## Labores agrícolas — Planificación, Ejecución y Cierre (HU-09 / HU-11)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/labores` | Todos | Lista las órdenes de trabajo ordenadas por `fechaPlanificada DESC`. Admite `?loteId=`, `?estado=` y `?tipoLabor=` |
| `GET` | `/api/labores/{id}` | Todos | Detalle de una labor agrícola con sus líneas de insumos (`labor_insumo`) |
| `POST` | `/api/labores` | `ADMIN`, `AGRONOMO` | Programa una orden de trabajo en estado `PLANIFICADA` (CU-03) y reserva stock (`RESERVADO`, RN-07) |
| `PUT` | `/api/labores/{id}` | `ADMIN`, `AGRONOMO` | Modifica una orden pendiente (`PLANIFICADA`, `APROBADA_CLIMA`, `BLOQUEADA_CLIMA`), reemplazando atómicamente sus reservas de insumos |
| `PATCH` | `/api/labores/{id}/reprogramar` | `ADMIN`, `AGRONOMO` | Reprograma la fecha planificada de una labor pendiente (`PLANIFICADA`, `APROBADA_CLIMA`, `BLOQUEADA_CLIMA`) conservando sus reservas (CU-08) |
| `POST` | `/api/labores/{id}/cancelar` | `ADMIN`, `AGRONOMO` | Cancela una labor pendiente (`CANCELADA`), pasa sus reservas a `LIBERADO` y descuenta `stock_reservado` (CU-08, RF-05) |
| `POST` | `/api/labores/{id}/iniciar` | `ADMIN`, `AGRONOMO`, `OPERARIO` | Inicia la ejecución en campo (`EN_EJECUCION`), consume el stock reservado (`CONSUMIDO` + `EGRESO_LABOR`) y pasa la maquinaria a `EN_LABOR` (CU-04, HU-11) |
| `POST` | `/api/labores/{id}/completar` | `ADMIN`, `AGRONOMO`, `OPERARIO` | Completa la labor (`COMPLETADA`) registrando `horasMaquinaConsumidas`, suma horas al horómetro, calcula `costoTotalCalculado` (RN-05) y si es `SIEMBRA` pasa el lote a `SEMBRADO` (HU-11) |

**Reglas que aplica el backend (CU-03, CU-04, CU-08, RN-03, RN-05, RN-07, RN-08, RN-10):**
* Coherencia agronómica con el estado del lote: en lotes `COSECHADO` o `DESCANSO` sólo se admite `LABRANZA`; `SIEMBRA` exige `EN_PREPARACION`; `LABRANZA` se bloquea si el lote tiene cultivo activo (`SEMBRADO`, `EN_CRECIMIENTO`, `LISTO_COSECHA`); `COSECHA` exige `LISTO_COSECHA` o `EN_CRECIMIENTO` (`409 Conflict`).
* Si se asigna maquinaria (`maquinariaId` opcional), debe encontrarse en estado `OPERATIVO` (RN-10) y no tener otra orden pendiente dentro de una ventana de ±2 horas (`409 Conflict`).
* Por cada insumo solicitado se verifica `stockDisponible = stockActual - stockReservado >= cantidadUtilizada` (RN-03). Si algún insumo no alcanza, se rechaza toda la operación (`409 Conflict`) informando la cantidad faltante sin reservar nada.
* Al iniciar la labor (`POST /api/labores/{id}/iniciar`), las labores de `LABRANZA` pasan directamente de `PLANIFICADA` a `EN_EJECUCION` y las que llevan regla climática exigen estar en `APROBADA_CLIMA` (RN-01, doc 06 §6.3.2). Cada línea `RESERVADO` pasa a `CONSUMIDO`, se descuenta `stock_actual` y `stock_reservado`, se fija `costo_subtotal = cantidadUtilizada * precioUnitario` vigente (RN-05), se registra un `movimiento_stock` de tipo `EGRESO_LABOR` asociado al usuario autenticado (RN-04) y la maquinaria pasa a `EN_LABOR` (RN-10).
* Al completar una labor en ejecución (`POST /api/labores/{id}/completar`, sólo desde `EN_EJECUCION`, RN-08), si tiene maquinaria asignada se exige `0 < horasMaquinaConsumidas <= 24 hs × días desde fechaEjecucion`, se suman las horas a `maquinaria.horasUsoActuales`, la máquina vuelve a `OPERATIVO` y se persiste de forma inmutable `costoTotalCalculado = SUM(costoSubtotal) + horasMaquinaConsumidas * costoOperativoHora` (RN-05). Si la labor es `SIEMBRA` y el lote está `EN_PREPARACION`, el lote pasa automáticamente a `SEMBRADO`.
* Al cancelar una labor pendiente (`PLANIFICADA`, `APROBADA_CLIMA` o `BLOQUEADA_CLIMA`), cada línea `RESERVADO` pasa a `LIBERADO` y se libera `insumo.stock_reservado` (RN-07). Las labores `EN_EJECUCION`, `COMPLETADA` o `CANCELADA` no admiten edición, reprogramación ni cancelación (`409 Conflict`, RN-08).

| Situación | Respuesta |
| :--- | :--- |
| Campos obligatorios faltantes, cantidades u horas de máquina inválidas o insumos duplicados | `400` |
| Programación, reprogramación o cancelación por `OPERARIO` | `403` |
| Lote, maquinaria, insumo o labor inexistente | `404` |
| Stock disponible/físico insuficiente, maquinaria no `OPERATIVO`, labor `BLOQUEADA_CLIMA` o transición de estado inválida | `409` |

## Clima actual de un lote (HU-08)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/lotes/{id}/clima` | Todos | Condiciones actuales en las coordenadas del lote, consultadas a [Open-Meteo](https://open-meteo.com) (sin API key) |

```json
{
  "loteId": 1, "loteNombre": "Lote Norte - La Escondida", "latitud": -33.892, "longitud": -60.573,
  "temperaturaC": 26.5, "vientoVelocidadKmh": 5.1, "probabilidadLluviaPct": 1, "humedadRelativaPct": 47,
  "precipitacionMm": 0.0, "fechaLectura": "2026-10-31T16:30:00", "origen": "API", "obtenidaEn": "2026-10-31T19:36:25Z"
}
```

Las variables y unidades coinciden con las de la evaluación de reglas climáticas (HU-07), así que la lectura se puede evaluar sin conversiones. `fechaLectura` es la hora local del lote informada por el proveedor; `obtenidaEn` es el instante (UTC) en que se consultó la API.

**Resiliencia (doc 07, sección 7.3):** la lógica depende de la interfaz `ProveedorClima`; el adaptador `ClimaClient` aplica, con Resilience4j y Caffeine:
* Timeout de 3 s por petición y hasta 2 reintentos con espera exponencial (500 ms, 1 s), sólo ante errores de red, HTTP 5xx o 429.
* Circuit breaker: se abre si falla el 50% de las últimas 10 llamadas, queda abierto 60 s y luego prueba con 3 llamadas.
* Si la API falla, se responde con la última lectura de las mismas coordenadas (redondeadas a 2 decimales) si tiene menos de 30 minutos, con `"origen": "CACHE"`. Si no hay, se responde `503` sin afectar al resto de los módulos (RNF-03).
* Los cambios de estado del circuito se registran en el log, y el estado del circuito, los reintentos y los aciertos de caché se publican como métricas de Micrometer (RNF-06).

| Situación | Respuesta |
| :--- | :--- |
| Lote inexistente | `404` |
| API climática caída y sin lectura en caché vigente | `503` |

## Autorización climática de labores (HU-10)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/labores/{id}/validar-clima` | `ADMIN`, `AGRONOMO`, `OPERARIO` | Consulta el clima actual del lote (HU-08), lo evalúa contra la regla del tipo de labor (HU-07) y deja la labor en `APROBADA_CLIMA` o `BLOQUEADA_CLIMA` (CU-04, RN-01) |
| `POST` | `/api/labores/{id}/autorizacion-manual` | `ADMIN`, `AGRONOMO` | Aprueba la labor sin datos climáticos cuando la API no responde y no hay caché vigente, con una justificación obligatoria (RN-11) |

```json
{
  "aprobada": false, "autorizacionManual": false, "estadoResultante": "BLOQUEADA_CLIMA",
  "incumplimientos": ["Viento (25 km/h) supera el máximo permitido (15.00 km/h)"],
  "mensajeAlerta": "Viento superior a 15 km/h genera deriva peligrosa de fitosanitarios o lluvia inminente.",
  "temperaturaC": 24.5, "vientoVelocidadKmh": 25, "probabilidadLluviaPct": 10, "humedadRelativaPct": 55,
  "fechaLectura": "2026-11-06T09:30:00", "origen": "API", "obtenidaEn": "2026-11-06T12:31:05Z",
  "labor": { "id": 3, "estado": "BLOQUEADA_CLIMA", "vientoVelocidadKmh": 25.00, "...": "..." }
}
```

* Se valida desde `PLANIFICADA` o `BLOQUEADA_CLIMA` (revalidación). Las labores de `LABRANZA` no requieren validación (RN-01).
* El resultado de la evaluación se informa siempre con `200`: si alguna variable está fuera de umbral la labor queda en `BLOQUEADA_CLIMA` con los incumplimientos y el `mensajeAlerta` de la regla; las reservas de stock se mantienen. En ambos casos se guardan en la labor la temperatura, el viento, la probabilidad de lluvia y la humedad usados.
* Si la API falla se usa la lectura en caché del lote (`"origen": "CACHE"`). Sin API ni caché se responde `503` y la labor no cambia (RF-06, criterio 5).
* La consulta al proveedor se hace fuera de la transacción; luego la labor se bloquea (`FOR UPDATE`) y se vuelve a verificar su estado antes de aplicar el resultado.
* La autorización manual consulta primero el clima: si el servicio responde (por API o caché) se rechaza con `409`. Si procede, la labor pasa a `APROBADA_CLIMA` y se agrega a `observacionesClimaticas` quién autorizó, cuándo y la justificación.

| Situación | Respuesta |
| :--- | :--- |
| Justificación vacía o con menos de 10 caracteres | `400` |
| Autorización manual por `OPERARIO` | `403` |
| Labor inexistente | `404` |
| Labor de `LABRANZA`, en estado distinto de `PLANIFICADA`/`BLOQUEADA_CLIMA`, tipo sin regla climática o autorización manual con el servicio climático disponible | `409` |
| API climática caída y sin lectura en caché vigente | `503` |

## Registro de cosechas (HU-12)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/cultivos` | Todos | Catálogo de cultivos ordenado por nombre (selector del formulario de cosecha) |
| `GET` | `/api/cosechas` | Todos | Cosechas registradas, de la más reciente a la más antigua. Admite `?loteId=` |
| `GET` | `/api/cosechas/{id}` | Todos | Detalle de una cosecha |
| `POST` | `/api/cosechas` | `ADMIN`, `AGRONOMO` | Registra la cosecha consolidada que cierra el ciclo productivo del lote (CU-05), asociada al usuario del token (RN-04) |

```bash
curl -X POST http://localhost:8080/api/cosechas \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"loteId": 3, "cultivoId": 3, "fechaCosecha": "2026-11-11", "toneladasTotales": 1350, "precioVentaPorTon": 210000, "laborId": null}'
```

```json
{
  "id": 1, "loteId": 3, "loteNombre": "Lote Este - Las Acacias", "superficieHa": 150.00,
  "cultivoId": 3, "cultivoNombre": "Maíz Tardío", "cultivoEspecie": "Maíz", "laborId": null,
  "fechaCosecha": "2026-11-11", "toneladasTotales": 1350.00, "rindeTonPorHa": 9.00, "precioVentaPorTon": 210000.00,
  "ingresoBrutoTotal": 283500000.00, "costosCicloImputados": 0.00, "margenNetoCalculado": 283500000.00,
  "roiPct": null, "usuarioUsername": "agronomo1"
}
```

**Reglas que aplica el backend (CU-05, RN-02, RN-04, RN-05, RN-12):**
* Sólo se registra la cosecha de un lote en `LISTO_COSECHA` (RN-02). Al registrarla, el lote pasa automáticamente a `COSECHADO` en la misma transacción. El lote se bloquea (`FOR UPDATE`) para que dos registros simultáneos no cierren dos veces el ciclo.
* La `fechaCosecha` no puede ser futura y debe ser posterior a la última cosecha del lote: cada registro cierra un ciclo distinto (RN-12).
* Campos calculados, fijados al registrar y no recalculados (RN-05):
  * `rindeTonPorHa = toneladasTotales / lote.superficieHa`
  * `ingresoBrutoTotal = toneladasTotales * precioVentaPorTon`
  * `margenNetoCalculado = ingresoBrutoTotal - SUM(costo_total_calculado)` de las labores `COMPLETADA` del lote ejecutadas en la ventana `(fecha_cosecha_anterior, fecha_cosecha]` (RN-12). Se usa el mismo criterio de fecha que el Tablero Financiero (HU-13): la de ejecución o, si falta, la planificada. Puede ser negativo (pérdida).
  * La respuesta agrega `costosCicloImputados` (`ingreso - margen`) y `roiPct`, que es `null` si el ciclo no tuvo costos.
* `laborId` es opcional y vincula la orden de trilla. Tiene que ser una labor de `COSECHA`, `COMPLETADA`, del mismo lote y del ciclo actual, y no puede estar vinculada a otra cosecha (`labor_id` es `UNIQUE`).
* Las cosechas no se editan ni se eliminan: son el cierre inmutable del ciclo productivo (RN-04, RN-12). Un lote con cosechas tampoco se puede eliminar (HU-03).

| Situación | Respuesta |
| :--- | :--- |
| Campos obligatorios faltantes, valores negativos, fecha futura o anterior a la última cosecha, labor de otro lote, de otro tipo o con fecha posterior a la cosecha, o valores calculados que exceden la precisión de la base | `400` |
| Registro por un usuario con rol `OPERARIO` | `403` |
| Lote, cultivo, labor o cosecha inexistente | `404` |
| Lote que no está `LISTO_COSECHA`, labor de cosecha no `COMPLETADA`, ya vinculada a otra cosecha o de un ciclo anterior | `409` |

## Tablero financiero — Margen Neto y ROI por lote (HU-13)

| Método | Endpoint | Roles | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tablero-financiero` | `ADMIN`, `AGRONOMO` | Resumen financiero consolidado de campaña (KPIs globales) más el reporte detallado por cada lote. Admite `?estadoLote=`, `?soloConCosecha=true`, `?fechaDesde=YYYY-MM-DD`, `?fechaHasta=YYYY-MM-DD` y `?alcanceCiclo=HISTORICO\|ULTIMO_CICLO` |
| `GET` | `/api/tablero-financiero/lotes` | `ADMIN`, `AGRONOMO` | Lista de reportes financieros por lote (admite `?estadoLote=`, `?soloConCosecha=true`, `?fechaDesde=`, `?fechaHasta=` y `?alcanceCiclo=`) |
| `GET` | `/api/tablero-financiero/lotes/{loteId}` | `ADMIN`, `AGRONOMO` | Reporte financiero de un lote específico con el desglose de sus labores `COMPLETADA` y sus registros de cosecha (admite `?fechaDesde=`, `?fechaHasta=` y `?alcanceCiclo=`) |

**Reglas y fórmulas que aplica el backend (Módulo 9, RF-08, RN-05, RN-12, RNF-02):**
* `costosTotalesAcumulados` suma los `costo_total_calculado` históricos e inmutables (RN-05) de las labores en estado `COMPLETADA` del lote dentro del alcance consultado, desglosando `costoInsumosAcumulado` y `costoMaquinariaAcumulado`, y calcula `costoPorHa = costosTotalesAcumulados / superficieHa`. Las órdenes pendientes o en ejecución no suman al costo consolidado hasta completarse.
* **Delimitación por ciclo productivo (`RN-12`):**
  * Con `alcanceCiclo=HISTORICO` (valor por defecto), se consolidan todas las labores completadas y cosechas registradas del lote (acotadas opcionalmente por `fechaDesde` / `fechaHasta`).
  * Con `alcanceCiclo=ULTIMO_CICLO`, el cálculo se acota a la ventana temporal `(fecha_cosecha_anterior, fecha_ultima_cosecha]` del último ciclo productivo cerrado (o a la campaña activa posterior si el lote ya reingresó a preparación/siembra tras su última cosecha).
* Si el lote registra cosechas en el período (`cosechaRegistrada = true`), se consolidan `toneladasTotales`, `rindeTonPorHa = toneladasTotales / superficieHa`, `ingresoBrutoTotal = SUM(ingreso_bruto_total)` y `margenNeto = ingresoBrutoTotal - costosTotalesAcumulados`.
* Cálculo de ROI (RF-08):
  * Con cosecha registrada y `costosTotalesAcumulados > 0` (criterio 1): `roiPct = (margenNeto / costosTotalesAcumulados) * 100` (`estadoRoi = "CALCULADO"`).
  * Sin cosecha registrada y `costosTotalesAcumulados > 0` (criterio 2): se exponen únicamente los costos acumulados y el ROI queda en `estadoRoi = "PENDIENTE"` (`roiPct = null`, `ingresoBrutoTotal = null`, `margenNeto = null`).
  * Con `costosTotalesAcumulados == 0` (criterio 3): se evita la división por cero y se informa `estadoRoi = "SIN_COSTOS"` (`"Sin costos registrados"` / `"Pendiente (sin costos registrados)"`).
* Carga masiva sin consultas N+1 para cumplir el tiempo de respuesta `< 2 s` con hasta 500 lotes (RNF-02).

| Situación | Respuesta |
| :--- | :--- |
| Rango de fechas inválido (`fechaDesde > fechaHasta`) | `400` |
| Consulta por un usuario con rol `OPERARIO` (doc 07 §7.2) | `403` |
| Lote inexistente en `/api/tablero-financiero/lotes/{loteId}` | `404` |

## Configuración útil

| Variable | Default | Uso |
| :--- | :--- | :--- |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos (separados por coma) para el frontend |
| `JWT_SECRET` | *(sólo `prod`, obligatoria)* | Secreto para firmar los JWT (mínimo 32 caracteres). En `dev` se usa un secreto fijo de desarrollo, por lo que los tokens sobreviven a un reinicio |
| `CLIMA_BASE_URL` | `https://api.open-meteo.com` | URL base del proveedor meteorológico. El resto de los parámetros de resiliencia está en `app.clima.*` de `application.yml` |

> En `prod` la app **no arranca** sin `JWT_SECRET`. Además, las contraseñas de `data.sql` son sólo para desarrollo: cambiarlas si se carga ese archivo en un ambiente real.

## Estructura

```text
com.grupo107.agtech
├── BackendApplication
├── config/        # CORS y configuración transversal
├── controller/    # Controllers REST (prefijo /api) y manejo de errores
├── model/         # Entidades JPA
├── repository/    # Repositorios Spring Data
├── service/       # Lógica de negocio y reglas transaccionales
└── security/      # JWT, filtro de autenticación y reglas de acceso (HU-14)
```
