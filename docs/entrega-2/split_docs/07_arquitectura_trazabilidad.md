# 7. Arquitectura Detallada y Trazabilidad

## 7.1. Arquitectura Detallada

El sistema se ha diseñado bajo una **Arquitectura de Capas (N-Tier)** enfocada en el paradigma de API REST, promoviendo alta cohesión y bajo acoplamiento.

### Componentes de la Arquitectura:

1.  **Capa de Presentación (Frontend):**
    *   **Tecnología:** React (Single Page Application).
    *   **Responsabilidad:** Interfaz de usuario (UI), visualización de los tableros de ROI, formularios y manejo del token JWT en el cliente.
2.  **Capa de Servicios y Negocio (Backend):**
    *   **Tecnología:** Java 17+ con Spring Boot 3.x.
    *   **Componentes Principales:**
        *   **Controllers (API REST):** Exponen los endpoints (ej. `/api/lotes`, `/api/labores`).
        *   **Services:** Contienen la lógica pura, implementando las Reglas de Negocio (validación climática, costeo, transiciones de estado).
        *   **Security (Spring Security + JWT):** Intercepta peticiones para asegurar que solo usuarios autorizados (según su rol) ejecuten endpoints críticos.
        *   **Cliente Climático (`ClimaClient`):** Adaptador que encapsula la comunicación con la API externa y aplica los patrones de resiliencia de la sección 7.3. Los servicios dependen de una interfaz (`ProveedorClima`), no de la API concreta, por lo que el proveedor puede cambiarse sin modificar la lógica de negocio.
        *   **Tareas Programadas (`@Scheduled`):** Evaluación periódica de alertas, vencimiento de aprobaciones climáticas y control de consistencia de stock.
3.  **Capa de Acceso a Datos (Persistencia):**
    *   **Tecnología:** Spring Data JPA (Hibernate).
    *   **Responsabilidad:** Abstracción y mapeo objeto-relacional (ORM) de las entidades del DER.
4.  **Capa de Base de Datos:**
    *   **Tecnología:** H2 (Entorno Local/Testing) / PostgreSQL (Entorno Producción).
    *   **Responsabilidad:** Almacenamiento seguro, garantizando integridad referencial, restricciones (CHECKs) y soporte WKT para coordenadas y polígonos.
5.  **Integraciones Externas:**
    *   **API Climática:** Servicio de terceros consultado por el backend para obtener variables meteorológicas en tiempo real basadas en la latitud/longitud del lote.

## 7.2. Permisos por Rol

La autorización se implementa con Spring Security (`@PreAuthorize` sobre los controllers) en base al claim `rol` del JWT. La siguiente matriz es la referencia para los criterios de aceptación de RF-01.

**Leyenda:** ✔ = permitido · 👁 = sólo consulta · ✖ = denegado (`403 Forbidden`)

| Funcionalidad | ADMIN | AGRONOMO | OPERARIO |
| :--- | :---: | :---: | :---: |
| Gestionar usuarios y roles | ✔ | ✖ | ✖ |
| ABM de lotes y cambio de estado agronómico (CU-07) | ✔ | ✔ | 👁 |
| ABM de proveedores | ✔ | 👁 | ✖ |
| Registrar compras de insumos (CU-02) | ✔ | ✖ | ✖ |
| ABM de insumos y ajustes de stock | ✔ | 👁 | 👁 |
| Consultar historial de movimientos de stock | ✔ | ✔ | 👁 |
| ABM de maquinaria | ✔ | 👁 | 👁 |
| Registrar mantenimiento (CU-06) | ✔ | ✔ | ✔ |
| Modificar reglas climáticas | ✔ | 👁 | ✖ |
| Programar labores (CU-03) | ✔ | ✔ | ✖ |
| Cancelar / reprogramar labores (CU-08) | ✔ | ✔ | ✖ |
| Iniciar y completar labores (CU-04) | ✔ | ✔ | ✔ |
| Autorizar manualmente una labor por contingencia (RN-11) | ✔ | ✔ | ✖ |
| Registrar cosecha (CU-05) | ✔ | ✔ | ✖ |
| Consultar tablero financiero y ROI (RF-08) | ✔ | ✔ | ✖ |
| Consultar alertas (CU-09) | Todas | Stock, clima, cosecha, mantenimiento | Clima y mantenimiento |

## 7.3. Resiliencia ante Fallos de la API Climática

La API climática es la única dependencia externa del sistema y es crítica para RF-06. Para cumplir RNF-03, su falla **no debe afectar al resto de los módulos** ni dejar labores en estados inconsistentes. Se aplican los siguientes patrones, implementados con la librería **Resilience4j** (integración nativa con Spring Boot) y la caché de Spring (`@Cacheable`, con Caffeine):

| Patrón | Configuración propuesta | Objetivo |
| :--- | :--- | :--- |
| **Timeout** | 3 segundos por petición (`RestClient`/`WebClient`). | Evitar que un hilo quede bloqueado esperando una respuesta. |
| **Retry** | Hasta 2 reintentos con espera exponencial (500 ms, 1 s), sólo ante errores de red o HTTP 5xx/429. | Absorber fallas transitorias. |
| **Circuit Breaker** | Se abre si falla el 50% de las últimas 10 llamadas; permanece abierto 60 s y luego pasa a semiabierto con 3 llamadas de prueba. | Dejar de consultar una API caída y responder inmediatamente. |
| **Caché** | Clave: coordenadas redondeadas a 2 decimales (~1 km). Vigencia: **30 minutos**. | Reutilizar lecturas recientes y reducir consumo de la cuota de la API. |
| **Fallback** | Ver flujo siguiente. | Mantener la operatoria con una decisión segura. |

**Flujo de contingencia (CU-04, flujo 3a):**
1. Si la API responde, se usan los datos y se actualiza la caché.
2. Si falla (timeout, reintentos agotados o circuito abierto) y existe una lectura en caché con **menos de 30 minutos**, se valida con esos datos y se deja constancia en `observaciones_climaticas` ("Validado con datos en caché de HH:MM").
3. Si no hay caché vigente, la labor **no cambia de estado** (no se bloquea ni se aprueba, porque no hay datos para decidir) y el backend responde `503 Service Unavailable` con un mensaje claro para el usuario.
4. Un `AGRONOMO` o `ADMIN` puede autorizar manualmente el inicio (RN-11). La labor pasa a `APROBADA_CLIMA` y se registra en `observaciones_climaticas` la justificación y el usuario que autorizó. Las variables climáticas quedan nulas.
5. Cada apertura del circuito genera un evento en el log y en las métricas (ver doc 09) para que el equipo detecte la caída.

**Decisión de diseño:** se prefiere *no aprobar automáticamente* sin datos (*fail-safe*), porque aplicar fitosanitarios con viento excesivo provoca deriva, pérdida de producto y riesgo ambiental; el costo de demorar una labor es menor que el de ejecutarla en malas condiciones.

## 7.4. Matriz de Trazabilidad

Esta matriz relaciona cada Requerimiento Funcional (RF) con las Reglas de Negocio (RN), Requerimientos No Funcionales (RNF), Casos de Uso (CU), Módulos y Entidades del modelo de datos que lo soportan, asegurando cobertura total.

| RF | Reglas de Negocio | RNF | Casos de Uso | Módulo (doc 04) | Entidades |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RF-01** Usuarios y Seguridad | RN-04 | RNF-01, RNF-06 | CU-01 | Módulo 1 | `usuario` |
| **RF-02** Lotes y Catastro | RN-02, RN-08 | RNF-04, RNF-05 | CU-07 | Módulo 2 | `lote`, `usuario` |
| **RF-03** Inventario y Compras | RN-03, RN-04, RN-07, RN-09 | RNF-01 | CU-02 | Módulo 3, Módulo 4 | `proveedor`, `compra_insumo`, `detalle_compra`, `insumo`, `movimiento_stock` |
| **RF-04** Maquinaria | RN-06, RN-08, RN-10 | RNF-01 | CU-06 | Módulo 5 | `maquinaria`, `registro_mantenimiento` |
| **RF-05** Labores Agrícolas | RN-03, RN-05, RN-07, RN-08, RN-10 | RNF-01, RNF-02 | CU-03, CU-04, CU-08 | Módulo 7 | `labor_agricola`, `labor_insumo`, `insumo`, `movimiento_stock`, `maquinaria`, `lote` |
| **RF-06** Asistente Climático | RN-01, RN-11 | RNF-03, RNF-06 | CU-04 | Módulo 6 | `regla_climatica`, `labor_agricola`, `lote` |
| **RF-07** Alertas | RN-01, RN-06 | RNF-05, RNF-06 | CU-09 | Módulo 8 | `insumo`, `maquinaria`, `labor_agricola`, `lote` |
| **RF-08** Tablero y ROI | RN-02, RN-05 | RNF-02 | CU-05 | Módulo 9 | `registro_cosecha`, `cultivo`, `labor_agricola`, `lote` |

### 7.4.1. Cobertura inversa (Reglas de Negocio → Requerimientos)

Verifica que ninguna regla de negocio quede huérfana:

| RN | RF que la implementan | Dónde se valida |
| :--- | :--- | :--- |
| RN-01 Validación Climática | RF-06, RF-07 | `LaborService` + `ProveedorClima` |
| RN-02 Restricción de Cosecha | RF-02, RF-08 | `CosechaService` |
| RN-03 Consistencia de Inventario | RF-03, RF-05 | `LaborService` + `CHECK chk_insumo_stock` / `chk_insumo_reservado` |
| RN-04 Trazabilidad de Responsabilidad | RF-01, RF-03 | Servicios transaccionales (usuario tomado del JWT) |
| RN-05 Costeo Inmutable | RF-05, RF-08 | `LaborService` (no expone edición del costo) |
| RN-06 Mantenimiento Obligatorio | RF-04, RF-07 | `AlertaScheduler` |
| RN-07 Reserva de Stock | RF-03, RF-05 | `LaborService` |
| RN-08 Transiciones de Estado | RF-02, RF-04, RF-05 | Métodos de transición en las entidades/servicios |
| RN-09 Inmutabilidad del Historial | RF-03 | `MovimientoStockRepository` sin operaciones de update/delete |
| RN-10 Disponibilidad de Maquinaria | RF-04, RF-05 | `LaborService` |
| RN-11 Autorización Manual | RF-06 | `LaborService` + `@PreAuthorize` |
