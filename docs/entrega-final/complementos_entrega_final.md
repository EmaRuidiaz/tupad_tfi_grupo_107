# Complementos de Entrega Final — Sistema AgTech (Grupo 107)

Este documento reúne tres ejes complementarios definidos por el equipo para la instancia de entrega final del **Trabajo Final Integrador (TFI)**:

1. [Requisitos adicionales propuestos por el equipo para implementar](#1-requisitos-adicionales-propuestos-por-el-equipo-para-implementar)
2. [Documentación del uso de la IA y sus beneficios en el desarrollo](#2-documentación-del-uso-de-la-ia-y-sus-beneficios-en-el-desarrollo)
3. [Requerimientos funcionales a futuro](#3-requerimientos-funcionales-a-futuro)

---

## 1. Requisitos adicionales propuestos por el equipo para implementar

Más allá de los ABM básicos solicitados originalmente (lotes, insumos, maquinaria, labores y cosechas), el equipo decidió proponer e implementar requisitos técnicos y funcionales propios para acercar la plataforma a un escenario productivo real del sector agropecuario:

| Código / Eje | Requisito adicional propuesto | Implementación concreta en el repositorio | Valor agregado al dominio |
| :--- | :--- | :--- | :--- |
| **RP-01** | **Reserva lógica de stock y control de concurrencia en 4 capas (`RN-13`)** | Separación entre `stock_actual` y `stock_reservado` en `insumo`, tabla `labor_insumo` con estados (`RESERVADO`, `CONSUMIDO`, `LIBERADO`), `@Transactional`, bloqueo pesimista `SELECT ... FOR UPDATE` (`findByIdForUpdate`), ordenamiento determinista por `insumo_id ASC` en `LaborService` y `CompraService`, y restricciones `CHECK` en `database/schema.sql`. | Evita sobreasignación de insumos y *deadlocks* cuando dos usuarios planifican labores o registran compras al mismo tiempo. |
| **RP-02** | **Resiliencia ante fallos climáticos y contingencia manual auditada (`RN-11`)** | Adaptador `ClimaClient` (detrás del puerto `ProveedorClima`) con Resilience4j (*timeout* 3 s, hasta 2 reintentos exponenciales, *circuit breaker* al 50% en ventana de 10 llamadas), caché Caffeine de 30 min por coordenada redondeada y endpoint `POST /api/labores/{id}/autorizacion-manual` con justificación obligatoria para `ADMIN` y `AGRONOMO`. | Impide que una caída de Open-Meteo detenga el resto del sistema y evita aprobar labores sin criterio agronómico (*fail-safe*). |
| **RP-03** | **Costeo histórico inmutable y delimitación de ciclos productivos (`RN-05`, `RN-12`)** | Persistencia inmutable de `costo_subtotal`, `costo_total_calculado` y `margen_neto_calculado` al ejecutar/cerrar operaciones. Delimitación de campañas por ventana entre cosechas `(fecha_cosecha_anterior, fecha_cosecha_actual]` en `TableroFinancieroService`, con filtro `HISTORICO` / `ULTIMO_CICLO`, consultas optimizadas sin problema N+1, exportación CSV y simulador de punto de equilibrio en `TableroFinanciero.jsx`. | Garantiza la trazabilidad económica aunque cambien los precios de insumos o el costo operativo de la maquinaria en el futuro, soportando doble cultivo anual por lote. |
| **RP-04** | **Soporte GIS con validación de polígonos WKT** | Campo `poligono_wkt` en `lote` validado por `PoligonoWktValidator` (anillo cerrado con al menos 4 vértices en formato `POLYGON((lon lat, ...))`), manteniendo compatibilidad simultánea entre H2 (`dev`) y PostgreSQL (`prod`). | Permite representar el perímetro real de cada parcela sin perder portabilidad entre entornos ni romper el despliegue local inmediato. |
| **RP-05** | **Seguridad JWT por perfiles y Kardex inmutable (`HU-14`, `RN-04`, `RN-09`)** | Autenticación *stateless* con JWT (expiración 8 h), contraseñas con BCrypt, autorización granular `@PreAuthorize` para los roles `ADMIN`, `AGRONOMO` y `OPERARIO`, y registro de auditoría en `movimiento_stock` de sólo inserción (sin edición ni borrado). | Asegura que toda operación crítica (compras, ajustes de stock, labores, cosechas, mantenimientos) quede vinculada al usuario autenticado. |
| **RP-06** | **Módulo de Compras a Proveedores y Simulador Climático** | Implementación de `CompraController`, `ProveedorController`, `CompraService`, `Compras.jsx` y `CompraFormModal.jsx` (ingreso atómico de facturas que incrementa stock, actualiza precio unitario y genera `INGRESO_COMPRA` en el kardex), junto con `SimuladorClimaModal.jsx` para probar reglas climáticas antes de ir a campo. | Cierra el circuito completo de abastecimiento e inventario y facilita la verificación previa de umbrales agronómicos. |

---

## 2. Documentación del uso de la IA y sus beneficios en el desarrollo

Durante el desarrollo del proyecto, el equipo incorporó herramientas de **Inteligencia Artificial generativa** como soporte de ingeniería (*AI-assisted development*), siempre bajo supervisión, criterio técnico y validación humana del grupo.

### 2.1. Cómo se integró la IA en el flujo de trabajo

1. **Análisis, diseño y refinamiento documental:**
   * Apoyo para estructurar la documentación de la 2.ª Entrega (`docs/entrega-2/split_docs/01_introduccion.md` a `09_operacion_monitoreo.md`), formalizar matrices de trazabilidad (`RF` ↔ `RN` ↔ `CU` ↔ entidades), redactar diagramas Mermaid (estados, secuencia y actividad) y cruzar las devoluciones docentes (`feedback_profe.txt` / revisiones) contra cada artefacto.
2. **Asistencia en implementación Backend y Frontend:**
   * Aceleración de estructura repetitiva (*boilerplate* de DTOs, repositorios, componentes modales en React, validaciones Bean Validation) manteniendo las convenciones arquitectónicas definidas por el equipo.
   * Análisis conjunto de problemas de diseño no triviales, como el ordenamiento de bloqueos pesimistas por `insumo_id` para prevenir *deadlocks* (`RN-13`), la separación transaccional de la consulta HTTP al proveedor climático en `AutorizacionClimaticaService` y la prevención de consultas N+1 en `TableroFinancieroService`.
3. **Pruebas automatizadas y revisión previa a merge:**
   * Generación asistida de casos de prueba en JUnit / Spring Boot Test (ej. escenarios `400`/`401`/`403`/`404`/`409`/`503`, reglas de transición de estados y pruebas de resiliencia en `ClimaClientTests`) y verificación de consistencia entre código, `schema.sql` y documentación antes de cerrar cada Pull Request.

### 2.2. Beneficios concretos obtenidos

* **Mayor velocidad de entrega sin sacrificar estructura:** permitió iterar de forma ordenada por Historias de Usuario (`HU-01` a `HU-14`) en ramas dedicadas, destinando más tiempo a las reglas de negocio agronómicas y menos a código mecánico.
* **Detección temprana de casos borde y regresiones:** facilitó identificar validaciones faltantes (ej. retroceso de horómetros, repetición de insumos en una misma orden o compra, reajuste de secuencias `IDENTITY` en H2 tras el *seed* en `database/data.sql`, división por cero en el ROI).
* **Mayor cobertura de tests y legibilidad:** ayudó a estandarizar mensajes de error en `ApiExceptionHandler`, respuestas JSON uniformes de seguridad (`JsonSecurityErrorHandler`) y documentación técnica clara de endpoints en `backend/README.md`.
* **Consistencia entre artefactos:** contribuyó a mantener alineados el esquema SQL, la capa de servicios Java, las vistas React y la documentación técnica.

### 2.3. Buenas prácticas y límites adoptados por el equipo

* **Validación empírica obligatoria (*Human-in-the-Loop*):** ningún cambio sugerido por IA se integró sin lectura crítica del diff, revisión de permisos (`@PreAuthorize`), ejecución de pruebas automatizadas (`./mvnw test`) y verificación con el linter del frontend (`npm run lint`).
* **Cuidado de secretos y datos sensibles:** no se compartieron credenciales de producción ni claves reales; toda configuración productiva quedó externalizada por variables de entorno.
* **Comprensión compartida del código:** cada módulo fue revisado entre los integrantes del grupo para asegurar que todo el equipo pueda explicar y defender técnicamente cualquier decisión arquitectónica en la mesa de examen.

---

## 3. Requerimientos funcionales a futuro

De cara a la evolución del sistema posterior a la entrega del TFI, y diferenciando claramente lo ya implementado de lo proyectado como trabajo futuro, se identifican los siguientes requerimientos funcionales:

| ID Futuro | Requerimiento Funcional Proyectado | Relación con la arquitectura actual | Beneficio esperado |
| :--- | :--- | :--- | :--- |
| **RF-F01** | **Centro transversal de alertas y tareas programadas (`@Scheduled`)** | Hoy existen alertas por módulo (`stockCritico`, `mantenimientoVencido`). Falta un módulo unificado (`CU-09` / `RF-07`) en `Inicio.jsx` y *jobs* periódicos para expirar aprobaciones climáticas (`APROBADA_CLIMA` > 2 h) y notificar lotes con más de 15 días en `LISTO_COSECHA`. | Gestión proactiva centralizada al ingresar al sistema, evitando olvidos operativos en campañas simultáneas. |
| **RF-F02** | **ABM completo de Proveedores y Gestión de Usuarios desde la UI** | Actualmente `ProveedorController` es sólo lectura (`GET /api/proveedores`) para el formulario de compras, y los usuarios se administran en `database/data.sql`. *(Nota: el ABM pendiente está siendo desarrollado en la rama de otro integrante y resta integrarlo; la administración de usuarios/roles para `ADMIN` queda proyectada a futuro).* | Autonomía total del administrador sin intervención directa sobre la base de datos. |
| **RF-F03** | **GIS espacial nativo con PostGIS y mapas interactivos** | El modelo ya valida y almacena `poligono_wkt` como `TEXT` para mantener compatibilidad con H2. En una fase futura orientada exclusivamente a PostgreSQL/PostGIS se migrará a columnas `GEOMETRY(POLYGON)` con visualización cartográfica interactiva (ej. Leaflet) y cálculos de superposición/superficie reales. | Análisis geoespacial preciso, mapas de rinde por lote y validación geográfica de límites. |
| **RF-F04** | **Integración con estaciones meteorológicas locales y sensores IoT** | Gracias al desacoplamiento tras la interfaz `ProveedorClima`, se podrá sumar una implementación que consulte estaciones en campo o sensores de humedad de suelo sin modificar `AutorizacionClimaticaService`. | Mayor precisión microclimática por establecimiento respecto del pronóstico satelital general. |
| **RF-F05** | **Analítica predictiva y recomendaciones agronómicas asistidas por IA** | Aprovechar el historial plurianual de `registro_cosecha`, `labor_agricola` y variables climáticas guardadas en cada labor para estimar rindes esperados, detectar patrones de desvío de costos y sugerir ventanas óptimas de pulverización o siembra. | Soporte predictivo para la toma de decisiones estratégicas entre campañas. |
| **RF-F06** | **Gestión documental de comprobantes y cuenta corriente con proveedores** | Extender `compra_insumo` y `registro_cosecha` para adjuntar facturas/cartas de porte en almacenamiento de objetos y registrar pagos parciales o saldos pendientes. | Integración directa con la operatoria administrativa y contable del establecimiento. |
| **RF-F07** | **Modo offline para operación en campo (PWA)** | Adaptar la SPA React para que el rol `OPERARIO` pueda registrar el inicio/cierre de labores o mantenimientos sin señal de internet en el lote y sincronizar al recuperar conectividad. | Continuidad operativa en zonas rurales con baja cobertura móvil. |
