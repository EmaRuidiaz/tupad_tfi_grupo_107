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
SPRING_PROFILES_ACTIVE=prod DB_HOST=... DB_NAME=agtechdb DB_USER=... DB_PASSWORD=... ./mvnw spring-boot:run
```

* Las credenciales **no tienen valor por defecto** y deben venir de variables de entorno.
* `schema.sql` contiene `DROP TABLE`, por eso en `prod` la carga automática está deshabilitada (`spring.sql.init.mode: never`). Aplicarlo **una sola vez** de forma manual (`psql -f database/schema.sql`) y luego, opcionalmente, `data.sql`.
* Hibernate valida el esquema contra las entidades (`ddl-auto: validate`).

## Configuración útil

| Variable | Default | Uso |
| :--- | :--- | :--- |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos (separados por coma) para el frontend |

## Estructura

```text
com.grupo107.agtech
├── BackendApplication
├── config/        # CORS y configuración transversal
└── controller/    # Controllers REST (prefijo /api)
```
