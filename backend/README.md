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

## Configuración útil

| Variable | Default | Uso |
| :--- | :--- | :--- |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos (separados por coma) para el frontend |
| `JWT_SECRET` | *(sólo `prod`, obligatoria)* | Secreto para firmar los JWT (mínimo 32 caracteres). En `dev` se usa un secreto fijo de desarrollo, por lo que los tokens sobreviven a un reinicio |

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
