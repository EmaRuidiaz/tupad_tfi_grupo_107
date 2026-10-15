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
└── security/      # JWT, filtro de autenticación y reglas de acceso (HU-14)
```
