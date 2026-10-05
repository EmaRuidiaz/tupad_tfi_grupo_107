# 6. Reglas de Negocio y Casos de Uso

## 6.1. Reglas de Negocio (RN)

Las siguientes reglas dictan las restricciones lógicas y comerciales operativas del sistema AgTech:

*   **RN-01 Validación Climática:** Una labor agrícola cuyo `tipo_labor` tenga una `regla_climatica` asociada (pulverización, siembra, cosecha, fertilización) no puede pasar a `EN_EJECUCION` si las variables meteorológicas están fuera de los umbrales de la regla (ej. vientos fuertes o humedad por debajo de lo requerido). En ese caso pasa a `BLOQUEADA_CLIMA`. Las labores de `LABRANZA` no requieren validación climática.
*   **RN-02 Restricción de Cosecha:** Un lote no puede registrar una cosecha a menos que su estado agronómico actual sea `LISTO_COSECHA`. Al registrarla, el lote pasa a `COSECHADO`.
*   **RN-03 Consistencia de Inventario:** No es posible programar una labor si el **stock disponible** (`stock_actual - stock_reservado`) de alguno de los insumos requeridos es menor a la cantidad demandada. Al iniciarla, se verifica además que el `stock_actual` físico siga alcanzando.
*   **RN-04 Trazabilidad de Responsabilidad:** Absolutamente todas las labores ejecutadas, mantenimientos realizados, cosechas y movimientos de stock (compras o ajustes) deben quedar vinculados de forma inmutable al `usuario_id` que los autorizó.
*   **RN-05 Costeo Inmutable:** Una vez que una labor finaliza y se calcula su `costo_total_calculado`, este valor se vuelve histórico y no debe recalcularse retrospectivamente aunque el precio unitario del insumo cambie en el futuro.
*   **RN-06 Mantenimiento Obligatorio:** Si las horas de uso de una maquinaria superan las `horas_para_proximo_service`, la maquinaria generará una alerta de mantenimiento crítico, debiendo registrarse un servicio para reiniciar el contador.
*   **RN-07 Reserva de Stock:** Al programar una labor, las cantidades de insumos quedan reservadas (`estado_reserva = 'RESERVADO'`) y se suman a `insumo.stock_reservado`. La reserva se convierte en consumo al iniciar la labor o se libera al cancelarla.
*   **RN-08 Transiciones de Estado Controladas:** Los estados de `lote`, `labor_agricola`, `maquinaria` y `labor_insumo` sólo pueden cambiar según las transiciones definidas en la sección 6.3. Cualquier otra transición es rechazada por la capa de servicio.
*   **RN-09 Inmutabilidad del Historial de Stock:** Los registros de `movimiento_stock` no se modifican ni se eliminan. Las correcciones se realizan con un nuevo movimiento de tipo `AJUSTE` con observación obligatoria.
*   **RN-10 Disponibilidad de Maquinaria:** Sólo puede asignarse a una labor una maquinaria en estado `OPERATIVO`. Mientras la labor está `EN_EJECUCION`, la máquina permanece `EN_LABOR` y no puede asignarse a otra labor en ejecución.
*   **RN-11 Autorización Manual por Contingencia Climática:** Si la API climática no está disponible y no hay datos en caché vigentes, sólo un usuario `AGRONOMO` o `ADMIN` puede autorizar manualmente el inicio de la labor, registrando una justificación en `observaciones_climaticas`.

## 6.2. Casos de Uso (CU) Principales

A continuación se listan los Casos de Uso más representativos del sistema:

### CU-01: Iniciar Sesión (Login)
*   **Actor:** Usuario (Admin, Agrónomo, Operario).
*   **Descripción:** El usuario ingresa sus credenciales. El sistema las valida y devuelve un token JWT con los permisos asociados a su rol.
*   **Flujo alternativo:** Si las credenciales son inválidas, el sistema responde con un mensaje genérico de error sin indicar qué dato es incorrecto.

### CU-02: Registrar Compra de Insumos
*   **Actor:** Admin.
*   **Descripción:** El actor registra una factura de un proveedor, especificando los insumos, cantidades y precios. El sistema calcula subtotales y total, actualiza el `stock_actual` y el `precio_unitario` de cada insumo y crea registros en `movimiento_stock` con tipo `INGRESO_COMPRA`. Toda la operación es atómica.

### CU-03: Programar Labor Agrícola
*   **Actor:** Agrónomo.
*   **Precondiciones:** El lote existe y no está `COSECHADO` ni en `DESCANSO` (salvo labores de `LABRANZA`). La maquinaria elegida, si la hay, está `OPERATIVO`.
*   **Flujo Principal:**
    1. El actor selecciona el lote, el tipo de labor, la fecha planificada, la maquinaria y los insumos con sus cantidades.
    2. El sistema calcula el stock disponible de cada insumo (`stock_actual - stock_reservado`).
    3. El sistema crea la labor en estado `PLANIFICADA` y una línea `labor_insumo` por insumo en estado `RESERVADO`.
    4. El sistema incrementa `stock_reservado` de cada insumo (RN-07).
*   **Flujos Alternativos:**
    *   *2a. Stock insuficiente:* Si algún insumo no tiene stock disponible suficiente, el sistema rechaza la programación indicando la cantidad faltante y no reserva nada.

### CU-04: Ejecutar Labor (Validación Climática)
*   **Actor:** Operario / Agrónomo.
*   **Precondiciones:** El usuario debe estar autenticado con rol válido. La labor debe estar en estado `PLANIFICADA`, `APROBADA_CLIMA` o `BLOQUEADA_CLIMA` (revalidación). Debe existir stock físico suficiente de los insumos reservados.
*   **Flujo Principal:**
    1. El actor selecciona una labor planificada en la interfaz y presiona "Iniciar Ejecución".
    2. El sistema obtiene las coordenadas geográficas del lote asociado a la labor.
    3. El sistema backend consulta la API Climática externa pasándole las coordenadas.
    4. El sistema evalúa las variables devueltas (viento, temperatura, humedad, probabilidad de lluvia) contra la tabla `regla_climatica` correspondiente al `tipo_labor`.
    5. Al ser óptimo el clima, el sistema guarda los datos climáticos y pasa la labor a `APROBADA_CLIMA`.
    6. El sistema descuenta el inventario físico, pasa las reservas a `CONSUMIDO` y registra un `EGRESO_LABOR` en `movimiento_stock` por insumo.
    7. El sistema cambia el estado de la labor a `EN_EJECUCION` (y la maquinaria a `EN_LABOR`), registrando `fecha_ejecucion`.
    8. El sistema muestra un mensaje de éxito al usuario.
*   **Flujos Alternativos:**
    *   *4a. Clima Adverso:* Si una o más variables superan los umbrales permitidos, el sistema aborta el inicio, cambia el estado de la labor a `BLOQUEADA_CLIMA`, no descuenta stock (las reservas se mantienen), y emite una alerta transversal al usuario.
    *   *3a. API Climática no disponible:* El sistema aplica la estrategia de resiliencia (doc 07, sección 7.3). Si hay datos en caché vigentes, continúa en el paso 4 con esos datos. Si no los hay, la labor permanece en su estado actual y se informa al usuario; un Agrónomo/Admin puede autorizar manualmente con justificación (RN-11), continuando en el paso 5.
    *   *4b. Labor sin regla climática (`LABRANZA`):* Se omiten los pasos 2 a 5.
    *   *6a. Stock físico insuficiente:* Si entre la programación y el inicio hubo un ajuste que dejó stock insuficiente, el sistema rechaza el inicio indicando la cantidad faltante; la labor queda en `APROBADA_CLIMA` hasta que se reponga stock o venza la aprobación.

### CU-05: Registrar Cosecha y Consultar ROI
*   **Actor:** Agrónomo / Admin.
*   **Precondiciones:** El lote está en `LISTO_COSECHA` (RN-02).
*   **Descripción:** Al finalizar el ciclo del cultivo, se registra el rendimiento en toneladas y el precio de venta. El sistema calcula rinde, ingreso bruto y margen neto usando los costos históricos de las labores (RN-05), pasa el lote a `COSECHADO` y presenta el ROI final del lote en el Tablero de Control.

### CU-06: Registrar Mantenimiento de Maquinaria
*   **Actor:** Agrónomo / Operario.
*   **Precondiciones:** La maquinaria existe y no está `EN_LABOR`.
*   **Descripción:** El actor informa fecha, lectura del horómetro, tipo de mantenimiento, costo y horas del próximo service. El sistema crea el `registro_mantenimiento`, actualiza `maquinaria.horas_para_proximo_service`, cierra la alerta de mantenimiento pendiente y deja la máquina en `OPERATIVO`.

### CU-07: Gestionar Lotes (ABM)
*   **Actor:** Admin / Agrónomo.
*   **Descripción:** El actor da de alta, modifica o da de baja lotes con su nombre, superficie, coordenadas, polígono WKT y tipo de suelo. Los cambios de estado agronómico se validan según la sección 6.3.1. No se permite eliminar lotes con labores o cosechas asociadas.

### CU-08: Cancelar o Reprogramar Labor
*   **Actor:** Agrónomo.
*   **Precondiciones:** La labor está en `PLANIFICADA`, `APROBADA_CLIMA` o `BLOQUEADA_CLIMA`.
*   **Descripción:** Para **reprogramar**, el actor indica una nueva `fecha_planificada` y la labor vuelve a `PLANIFICADA` conservando sus reservas. Para **cancelar**, la labor pasa a `CANCELADA`, sus líneas pasan a `LIBERADO` y se descuentan de `stock_reservado`.

### CU-09: Consultar y Resolver Alertas
*   **Actor:** Admin / Agrónomo / Operario (cada rol ve las alertas que le corresponden).
*   **Descripción:** El actor consulta las alertas activas (stock crítico, mantenimiento, clima, cosecha demorada). Las alertas se resuelven automáticamente cuando desaparece la condición que las originó (ej. al registrar una compra o un service).

## 6.3. Estados de Negocio y Transiciones

Esta sección formaliza los estados definidos en los `CHECK` del modelo físico y **todas** las transiciones permitidas (RN-08). Los diagramas de estado correspondientes se encuentran en [08_diagramas_complementarios.md](./08_diagramas_complementarios.md#84-diagramas-de-estado).

### 6.3.1. Estados del Lote (`lote.estado`)

| Estado | Significado |
| :--- | :--- |
| `EN_PREPARACION` | Lote en barbecho o labranza, previo a la siembra. Estado inicial. |
| `SEMBRADO` | Siembra completada; cultivo aún no emergido. |
| `EN_CRECIMIENTO` | Cultivo emergido y en desarrollo. |
| `LISTO_COSECHA` | Cultivo en madurez comercial, apto para cosechar. |
| `COSECHADO` | Cosecha registrada; fin del ciclo productivo. |
| `DESCANSO` | Lote sin actividad productiva planificada. |

| Origen | Destino | Disparador | Quién |
| :--- | :--- | :--- | :--- |
| *(alta)* | `EN_PREPARACION` | Alta del lote (CU-07). | Admin / Agrónomo |
| `EN_PREPARACION` | `SEMBRADO` | Se completa una labor de tipo `SIEMBRA` en el lote. | Sistema (automático) |
| `SEMBRADO` | `EN_CRECIMIENTO` | Se registra la emergencia del cultivo. | Agrónomo |
| `EN_CRECIMIENTO` | `LISTO_COSECHA` | El agrónomo determina madurez comercial. | Agrónomo |
| `LISTO_COSECHA` | `COSECHADO` | Se registra la cosecha (CU-05). | Sistema (automático) |
| `COSECHADO` | `EN_PREPARACION` | Inicio de una nueva campaña. | Agrónomo |
| `COSECHADO` | `DESCANSO` | Se decide no sembrar en la próxima campaña. | Agrónomo |
| `DESCANSO` | `EN_PREPARACION` | Se retoma la actividad productiva. | Agrónomo |
| `SEMBRADO` / `EN_CRECIMIENTO` | `EN_PREPARACION` | Pérdida total del cultivo (granizo, helada, inundación). Requiere observación. | Agrónomo |

### 6.3.2. Estados de la Labor (`labor_agricola.estado`)

| Estado | Significado |
| :--- | :--- |
| `PLANIFICADA` | Labor programada con insumos reservados. Estado inicial. |
| `APROBADA_CLIMA` | Validación climática superada (o autorización manual, RN-11). Válida por **2 horas**. |
| `BLOQUEADA_CLIMA` | Validación climática no superada. Las reservas se mantienen. |
| `EN_EJECUCION` | Labor en curso; stock consumido y maquinaria en uso. |
| `COMPLETADA` | Labor finalizada con costo calculado. Estado final. |
| `CANCELADA` | Labor anulada; reservas liberadas. Estado final. |

| Origen | Destino | Disparador | Efectos |
| :--- | :--- | :--- | :--- |
| *(alta)* | `PLANIFICADA` | Programar labor (CU-03). | Reserva de insumos (RN-07). |
| `PLANIFICADA` / `BLOQUEADA_CLIMA` | `APROBADA_CLIMA` | Validación climática exitosa o autorización manual (CU-04). | Se guardan las variables climáticas. |
| `PLANIFICADA` / `BLOQUEADA_CLIMA` | `BLOQUEADA_CLIMA` | Validación climática fallida (CU-04, 4a). | Alerta de clima adverso. |
| `PLANIFICADA` | `EN_EJECUCION` | Inicio de labor sin regla climática (`LABRANZA`). | Consumo de stock; máquina `EN_LABOR`. |
| `APROBADA_CLIMA` | `EN_EJECUCION` | Inicio de la labor dentro de la ventana de 2 horas (CU-04). | Consumo de stock; máquina `EN_LABOR`. |
| `APROBADA_CLIMA` | `PLANIFICADA` | Vence la ventana de 2 horas sin iniciar (proceso automático). | Se debe revalidar el clima. |
| `BLOQUEADA_CLIMA` | `PLANIFICADA` | Reprogramación con nueva fecha (CU-08). | Se conservan las reservas. |
| `EN_EJECUCION` | `COMPLETADA` | El operario informa fin de labor y horas de máquina. | Cálculo de `costo_total_calculado`; suma de horas a la máquina; máquina `OPERATIVO`; si es `SIEMBRA`, lote a `SEMBRADO`. |
| `PLANIFICADA` / `APROBADA_CLIMA` / `BLOQUEADA_CLIMA` | `CANCELADA` | Cancelación (CU-08). | Reservas a `LIBERADO`. |

*Una labor `EN_EJECUCION` no puede cancelarse porque el stock ya fue consumido: si se interrumpe, se completa con las horas reales y el insumo sobrante se reingresa con un `AJUSTE`.*

### 6.3.3. Estados de la Maquinaria (`maquinaria.estado`)

| Origen | Destino | Disparador | Quién |
| :--- | :--- | :--- | :--- |
| *(alta)* | `OPERATIVO` | Alta de la máquina. | Admin |
| `OPERATIVO` | `EN_LABOR` | Una labor que la utiliza pasa a `EN_EJECUCION`. | Sistema (automático) |
| `EN_LABOR` | `OPERATIVO` | La labor pasa a `COMPLETADA`. | Sistema (automático) |
| `OPERATIVO` | `EN_MANTENIMIENTO` | Ingreso a taller (preventivo o por rotura). | Agrónomo / Operario |
| `EN_MANTENIMIENTO` | `OPERATIVO` | Se registra el service (CU-06). | Sistema (automático) |
| `OPERATIVO` / `EN_MANTENIMIENTO` | `FUERA_DE_SERVICIO` | Rotura grave o baja lógica. | Admin |
| `FUERA_DE_SERVICIO` | `EN_MANTENIMIENTO` | Se decide reparar la máquina. | Admin |

*Superar `horas_para_proximo_service` no cambia el estado de la máquina: genera la alerta de RN-06. La máquina sigue `OPERATIVO`, pero la interfaz advierte al asignarla a una labor.*

### 6.3.4. Estados de la Reserva de Insumo (`labor_insumo.estado_reserva`)

| Origen | Destino | Disparador | Efecto en `insumo` |
| :--- | :--- | :--- | :--- |
| *(alta)* | `RESERVADO` | Programar labor (CU-03). | `stock_reservado += cantidad` |
| `RESERVADO` | `CONSUMIDO` | La labor pasa a `EN_EJECUCION`. | `stock_reservado -= cantidad`; `stock_actual -= cantidad`; `EGRESO_LABOR` |
| `RESERVADO` | `LIBERADO` | La labor pasa a `CANCELADA`. | `stock_reservado -= cantidad` |

`CONSUMIDO` y `LIBERADO` son estados finales.

### 6.3.5. Estados de Catálogos sin ciclo de vida

Los campos `insumo.categoria`, `insumo.unidad_medida`, `maquinaria.tipo`, `registro_mantenimiento.tipo_mantenimiento`, `regla_climatica.tipo_labor`, `movimiento_stock.tipo` y `usuario.rol` son **clasificaciones fijas** (no estados): su conjunto de valores está restringido por `CHECK` y no admiten transiciones. Sólo un `ADMIN` puede modificar el rol de un usuario.
