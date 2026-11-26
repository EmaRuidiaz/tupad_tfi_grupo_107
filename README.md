# Sistema AgTech de Gestión y Optimización de Lotes de Cultivo

**Trabajo Final Integrador (TFI) — Grupo 107**

* **Integrantes:**
  * Roques Zeballos, Juan Martín (`juanmartinroqueszeballos@gmail.com`)
  * Ruidiaz, Emanuel Facundo (`emafruidiaz@gmail.com`)
  * Santini, Mauro Gonzalo (`mgs.argentum@gmail.com`)
* **Tutor:** Prof. Herrera Molas, Gerardo A.

---

## Resumen Ejecutivo

El presente proyecto consiste en una plataforma web integral **AgTech** orientada a optimizar la gestión operativa, el control de recursos y la toma de decisiones en establecimientos agropecuarios. La aplicación resuelve problemáticas críticas como la descoordinación frente a condiciones climáticas adversas, el desgaste imprevisto de maquinaria y la falta de visibilidad financiera por lote. Para lograrlo, integra administración completa mediante operaciones ABM (CRUD), control de stock con reserva lógica y kardex inmutable, validación meteorológica en tiempo real con resiliencia, seguimiento de horómetros y un tablero financiero de margen neto y ROI por lote y ciclo productivo.

---

## Despliegue Online y Entregables Finales

* **Frontend (Vercel):** https://tupad-tfi-grupo-107.vercel.app/
* **Backend API (Render):** https://tupad-tfi-grupo-107.onrender.com *(Health check: `/actuator/health` · Ping: `/api/ping`)*
* **Base de Datos (Producción):** PostgreSQL gestionado en **Neon** *(configurado mediante variables de entorno fuera del repositorio)*.
* **Informe Final (PDF):** [`docs/entrega-final/Informe_Final_Sistema_AgTech_Grupo107.pdf`](docs/entrega-final/Informe_Final_Sistema_AgTech_Grupo107.pdf) *(pendiente de incorporar el enlace al video final una vez grabado)*.
* **Video de Presentación Final:** *Pendiente de incorporación del enlace.*

> **Nota sobre el entorno gratuito en Render:** tras períodos de inactividad, la primera petición al backend puede demorar unos segundos hasta que el contenedor vuelva a estar activo (*cold start*).

---

## 1. Definición de la Problemática

En el sector agropecuario actual, la eficiencia operativa y la toma de decisiones basada en datos son factores críticos para la rentabilidad y la sostenibilidad. Los productores y administradores agrícolas enfrentan habitualmente los siguientes desafíos:

* **Descoordinación climática y operativa:** La ejecución de tareas críticas (como pulverización, fertilización, siembra o cosecha) bajo condiciones meteorológicas inadecuadas genera pérdidas significativas de insumos, menor eficacia en los tratamientos y riesgos ambientales.
* **Desgaste imprevisto de maquinaria:** La falta de un seguimiento preciso de las horas de uso y el esfuerzo operativo de tractores y vehículos agrícolas conduce a roturas imprevistas, costos de reparación elevados y tiempos improductivos (*downtime*).
* **Falta de visibilidad financiera y de inventarios:** La gestión desconectada del stock de insumos, compras a proveedores, consumo de maquinaria y rendimientos dificulta la medición precisa del Retorno de Inversión (ROI) por lote y campaña agrícola.

---

## 2. Solución Implementada y Módulos Funcionales

La plataforma centraliza la gestión agrícola con autenticación **JWT** y autorización por roles (`ADMIN`, `AGRONOMO`, `OPERARIO`):

* **Panel de Inicio (`Inicio`):** Resumen operativo con KPIs en tiempo real (lotes activos, hectáreas, labores en curso/pendientes/bloqueadas, alertas de stock crítico y servicios de maquinaria vencidos) y acceso directo a los módulos habilitados por rol.
* **Gestión de Lotes (ABM + GIS):** Administración de parcelas, superficie, coordenadas de centroide, validación de polígonos **WKT** (`POLYGON((lon lat, ...))`), máquina de estados agronómicos (`EN_PREPARACION` → `SEMBRADO` → `EN_CRECIMIENTO` → `LISTO_COSECHA` → `COSECHADO` / `DESCANSO`) y consulta del clima actual por lote.
* **Inventario de Insumos y Kardex Inmutable:** Control de stock físico (`stock_actual`) vs. stock reservado (`stock_reservado`), alertas de stock mínimo, ajustes manuales justificados y libro mayor inmutable de movimientos (`INGRESO_COMPRA`, `EGRESO_LABOR`, `AJUSTE`).
* **Compras y Proveedores:** ABM de proveedores e ingreso atómico de facturas de compra que incrementan stock, actualizan precio unitario y registran el movimiento `INGRESO_COMPRA` con bloqueo pesimista ordenado por `insumo_id`.
* **Maquinaria y Mantenimiento:** Registro de flota, horómetros protegidos contra retroceso, cálculo de costo operativo por hora, historial de services y actualización automática del próximo umbral de mantenimiento.
* **Reglas Climáticas y Asistente Meteorológico:** Configuración de umbrales por tipo de labor (`PULVERIZACION`, `SIEMBRA`, `COSECHA`, `FERTILIZACION`), simulador climático e integración con **Open-Meteo** mediante **Resilience4j** (timeout 3 s, hasta 2 reintentos, *circuit breaker* al 50%) y caché **Caffeine** de 30 minutos, más autorización manual auditada en contingencias (`RN-11`).
* **Labores Agrícolas (Órdenes de Trabajo):** Programación con reserva lógica de stock y bloqueo pesimista anti-deadlock (`SELECT ... FOR UPDATE`), reprogramación, cancelación con liberación de reserva, validación climática, inicio en campo y cierre con congelamiento histórico e inmutable de costos (`RN-05`).
* **Registro de Cosechas y Tablero Financiero (ROI):** Cierre de ciclo productivo por lote con cálculo de rinde (tn/ha), ingreso bruto, costos acumulados, margen neto y ROI (%) por lote, con filtros por campaña (`HISTORICO` / `ULTIMO_CICLO`), gráfico comparativo, simulador de punto de equilibrio y exportación CSV.

---

## 3. Stack Tecnológico

| Capa / Componente | Tecnología | Detalle de Uso |
| :--- | :--- | :--- |
| **Backend** | Java 17 + Spring Boot 4.1.1 | API REST multicapa, Spring Data JPA, Spring Security + JWT, Bean Validation, Resilience4j, Caffeine, Actuator + Micrometer |
| **Base de Datos (Dev)** | H2 Database | Base embebida en modo PostgreSQL para desarrollo local ágil y pruebas automatizadas |
| **Base de Datos (Prod)** | PostgreSQL (Neon) | Persistencia relacional desplegada en la nube (`ddl-auto: validate`) |
| **Frontend** | React 19 + Vite 8 + React Router 7 | SPA responsive con control de sesión JWT, formularios modales, simulador climático y tablero financiero |
| **API Externa** | Open-Meteo | Consulta meteorológica en tiempo real por latitud/longitud (sin API key) |
| **Despliegue** | Vercel + Render + Neon | Frontend en Vercel, backend contenedorizado con [`Dockerfile`](Dockerfile) en Render y PostgreSQL en Neon |

---

## 4. Instalación, Ejecución Local y Pruebas

### Prerrequisitos
* **Java 17+** (incluye Maven Wrapper `./mvnw` / `mvnw.cmd` en `backend/`)
* **Node.js 20+** y **npm** (probado con Node 22)

### 4.1. Backend (perfil `dev` con H2 en memoria)
```bash
cd backend
./mvnw spring-boot:run        # En Windows: .\mvnw.cmd spring-boot:run
```
* Carga automáticamente [`database/schema.sql`](database/schema.sql) y [`database/data.sql`](database/data.sql) con usuarios semilla (`admin`, `agronomo1`, `operario1`, clave de desarrollo `agtech123`).
* Endpoints de verificación local: `http://localhost:8080/api/ping` y `http://localhost:8080/actuator/health`.
* Para ejecutar la suite de pruebas automatizadas del backend:
```bash
cd backend
./mvnw test                   # En Windows: .\mvnw.cmd test
```
* Detalle completo de endpoints, reglas y variables de entorno de producción (`prod`) en [`backend/README.md`](backend/README.md) y [`backend/.env.example`](backend/.env.example).

### 4.2. Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
* Disponible en `http://localhost:5173` (consume por defecto `http://localhost:8080/api`, configurable vía `VITE_API_URL` según [`frontend/.env.example`](frontend/.env.example)).
* Verificación de lint y build de producción:
```bash
cd frontend
npm run lint
npm run build
```
* Más detalles en [`frontend/README.md`](frontend/README.md).

### 4.3. Base de Datos y Despliegue en Producción
* El esquema de las 14 tablas y los datos iniciales están en [`database/schema.sql`](database/schema.sql) y [`database/data.sql`](database/data.sql) (ver [`database/README.md`](database/README.md)).
* En producción (`SPRING_PROFILES_ACTIVE=prod`), las credenciales de PostgreSQL (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSL_MODE`), el secreto `JWT_SECRET` y `CORS_ALLOWED_ORIGINS` se inyectan exclusivamente por variables de entorno (ver [`backend/.env.example`](backend/.env.example)).

---

## 5. Limitaciones Conocidas y Trabajo Futuro

De acuerdo con el alcance acordado y las proyecciones documentadas en [`docs/entrega-final/complementos_entrega_final.md`](docs/entrega-final/complementos_entrega_final.md):
* **Usuarios y roles:** actualmente los usuarios (`ADMIN`, `AGRONOMO`, `OPERARIO`) se inicializan mediante `database/data.sql`; la administración de cuentas desde la interfaz queda proyectada como trabajo futuro (`RF-F02`).
* **Alertas y tareas programadas:** las alertas se visualizan en tiempo real en `Inicio` y en los módulos correspondientes; el centro unificado de notificaciones con historial y tareas `@Scheduled` (`RF-F01` / `CU-09`) queda proyectado para una siguiente iteración.
* **GIS:** la geometría se almacena y valida como texto **WKT** para preservar compatibilidad simultánea entre H2 (`dev`) y PostgreSQL (`prod`), dejando la migración nativa a PostGIS y mapas interactivos como evolución futura (`RF-F03`).
* **Dependencia externa:** el servicio meteorológico depende de **Open-Meteo**; ante indisponibilidad total sin caché vigente, el sistema responde `503` y habilita la autorización manual justificada por `ADMIN` o `AGRONOMO` (`RN-11`).

---

## 6. Estructura del Repositorio

```text
tupad_tfi_grupo_107/
├── backend/            # API REST en Java 17 + Spring Boot 4.1.1 + tests
├── frontend/           # SPA en React 19 + Vite 8 + configuración Vercel
├── database/           # Scripts DDL (schema.sql), DML seed (data.sql) y README
├── docs/               # Documentación de diseño (Entrega 2), backlog y entrega final
├── Dockerfile          # Empaquetado del backend para despliegue (Render)
├── .gitignore          # Exclusión de artefactos compilados y variables locales (.env)
└── README.md           # Documentación principal del proyecto
```

---

## 7. Documentación Técnica

* [**Índice de Documentación Técnica**](docs/README.md)
* [**Diseño y Módulos — 2.ª Entrega**](docs/entrega-2/diseno_y_modulos.md)
* [**Complementos de Entrega Final (Requisitos propios, Uso de IA y Trabajo Futuro)**](docs/entrega-final/complementos_entrega_final.md)
* [**Product Backlog (Historias de Usuario HU-01 a HU-14)**](docs/tablero_agil_backlog.md)
* [**Documentación de Backend**](backend/README.md) · [**Frontend**](frontend/README.md) · [**Base de Datos**](database/README.md)
