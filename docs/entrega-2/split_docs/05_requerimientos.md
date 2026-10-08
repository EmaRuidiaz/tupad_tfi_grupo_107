# 5. Requerimientos del Sistema

## 5.1. Criterio de Priorización

Los requerimientos se priorizan con el método **MoSCoW**, considerando el valor para el negocio y las dependencias técnicas entre módulos:

| Prioridad | Significado | Alcance |
| :--- | :--- | :--- |
| **Must** (Debe) | Imprescindible para que el sistema cumpla su objetivo. | Entra en el MVP de la entrega final. |
| **Should** (Debería) | Importante, pero el sistema funciona sin él. | Se desarrolla una vez cerrados los *Must*. |
| **Could** (Podría) | Deseable, mejora la experiencia. | Se incluye si el tiempo lo permite. |

## 5.2. Requerimientos Funcionales (RF)

| ID | Requerimiento | Prioridad | Depende de |
| :--- | :--- | :--- | :--- |
| RF-01 | Gestión de Usuarios y Seguridad | Must | — |
| RF-02 | Gestión de Lotes (Catastro) | Must | RF-01 |
| RF-03 | Gestión de Inventario y Compras | Must | RF-01 |
| RF-05 | Gestión de Labores Agrícolas | Must | RF-02, RF-03 (RF-04 para costeo de maquinaria) |
| RF-06 | Asistente Climático | Must | RF-05 |
| RF-04 | Gestión de Maquinaria | Should | RF-01 |
| RF-08 | Tablero Financiero (ROI) | Should | RF-05 |
| RF-07 | Sistema de Alertas | Could | RF-03, RF-04, RF-06 |

*   **RF-01 Gestión de Usuarios y Seguridad:** El sistema debe permitir la autenticación de usuarios mediante credenciales seguras (JWT), asignando roles específicos (ADMIN, AGRONOMO, OPERARIO) que delimiten el acceso a las funciones.
    *   **Criterios de aceptación:**
        1. Dado un usuario con credenciales válidas, cuando inicia sesión, entonces recibe un token JWT que incluye su rol y expira a las 8 horas.
        2. Dado un usuario con credenciales inválidas, cuando intenta iniciar sesión, entonces recibe `401 Unauthorized` sin indicar cuál de los dos datos es incorrecto.
        3. Dado un usuario autenticado, cuando invoca un endpoint no permitido para su rol (ver matriz de permisos, doc 07), entonces recibe `403 Forbidden`.
        4. Dada una petición sin token o con token vencido, cuando accede a cualquier endpoint distinto de `/api/auth/login`, entonces recibe `401 Unauthorized`.

*   **RF-02 Gestión de Lotes (Catastro):** El sistema debe permitir el alta, baja, y modificación (ABM) de lotes agrícolas, incluyendo sus coordenadas geográficas (polígonos) y características agronómicas.
    *   **Criterios de aceptación:**
        1. Dado un ADMIN o AGRONOMO, cuando da de alta un lote con nombre único, superficie mayor a 0 y coordenadas válidas, entonces el lote queda creado en estado `EN_PREPARACION`.
        2. Dado un nombre de lote ya existente o una superficie menor o igual a 0, cuando se intenta guardar, entonces el sistema rechaza la operación con un mensaje de validación (`400 Bad Request`).
        3. Dado un polígono WKT mal formado, cuando se guarda el lote, entonces se rechaza indicando el error de formato.
        4. Dado un lote con labores o cosechas asociadas, cuando se intenta eliminar, entonces el sistema lo impide (`409 Conflict`).
        5. Dado un cambio de estado del lote, cuando no corresponde a una transición permitida (doc 06, sección 6.3.1), entonces se rechaza.

*   **RF-03 Gestión de Inventario y Compras:** El sistema debe registrar las compras a proveedores, actualizando automáticamente el stock de insumos y registrando un historial detallado de movimientos.
    *   **Criterios de aceptación:**
        1. Dada una compra con al menos un detalle válido (cantidad mayor a 0), cuando se confirma, entonces se incrementa el `stock_actual` de cada insumo y se crea un `movimiento_stock` de tipo `INGRESO_COMPRA` por línea.
        2. Dada una compra confirmada, entonces `total_compra` es igual a la suma de los `subtotal` de sus detalles y el `precio_unitario` del insumo se actualiza al de la compra.
        3. Dado un error en cualquiera de las líneas, cuando se confirma la compra, entonces no se persiste nada (operación atómica).
        4. Dado un ajuste manual de stock, cuando el usuario no carga una observación, entonces el sistema lo rechaza.
        5. Dado un insumo, cuando se consulta su historial, entonces se listan todos sus movimientos con fecha, tipo, cantidad y usuario responsable.

*   **RF-04 Gestión de Maquinaria:** El sistema debe administrar el parque de maquinaria, registrar las horas de uso y programar/registrar servicios de mantenimiento.
    *   **Criterios de aceptación:**
        1. Dada una máquina con matrícula/serie única, cuando se da de alta, entonces queda en estado `OPERATIVO` con su horómetro inicial.
        2. Dada una labor completada con maquinaria, entonces `horas_uso_actuales` de la máquina se incrementa en las horas consumidas por la labor.
        3. Dado el registro de un service, entonces se actualiza `horas_para_proximo_service` con el valor informado y la máquina vuelve a `OPERATIVO`.
        4. Dada una máquina en `EN_MANTENIMIENTO` o `FUERA_DE_SERVICIO`, cuando se intenta asignar a una labor, entonces el sistema lo impide.

*   **RF-05 Gestión de Labores Agrícolas:** El sistema debe permitir programar, ejecutar y finalizar labores agrícolas en los lotes, descontando insumos del stock y calculando automáticamente el costo total en base a insumos y uso de maquinaria.
    *   **Criterios de aceptación:**
        1. Dada una labor programada cuyos insumos tienen stock disponible (`stock_actual - stock_reservado`) suficiente, entonces se crea en estado `PLANIFICADA` y los insumos quedan `RESERVADO`.
        2. Dado un insumo sin stock disponible suficiente, cuando se programa la labor, entonces se rechaza indicando la cantidad faltante.
        3. Dada una labor iniciada, entonces se descuenta el `stock_actual`, se libera la reserva y se registra un `EGRESO_LABOR` por insumo.
        4. Dada una labor completada, entonces `costo_total_calculado = Σ costo_subtotal + horas_maquina_consumidas × costo_operativo_hora` y ese valor no cambia aunque luego varíen los precios.
        5. Dada una labor `PLANIFICADA` o `BLOQUEADA_CLIMA` que se cancela, entonces sus reservas pasan a `LIBERADO` y se restan de `stock_reservado`.

*   **RF-06 Asistente Climático:** El sistema debe verificar las condiciones climáticas (API externa) antes de permitir la ejecución de ciertas labores, bloqueando aquellas en condiciones adversas (ej. ráfagas fuertes, humedad baja).
    *   **Criterios de aceptación:**
        1. Dada una labor de un tipo con `regla_climatica` asociada, cuando se inicia, entonces el sistema consulta el clima con la latitud/longitud del lote y compara viento, temperatura, humedad y probabilidad de lluvia contra los umbrales.
        2. Dado que todas las variables están dentro de los umbrales, entonces la labor pasa a `EN_EJECUCION` y se guardan los valores climáticos registrados.
        3. Dado que al menos una variable está fuera de umbral, entonces la labor pasa a `BLOQUEADA_CLIMA`, no se descuenta stock y se informa qué variable incumplió y el `mensaje_alerta` de la regla.
        4. Dada una labor de tipo `LABRANZA` (sin regla climática), entonces se inicia sin consultar la API.
        5. Dada una falla de la API climática, entonces el sistema aplica la estrategia de contingencia del doc 07, sección 7.3, sin dejar la labor en un estado inconsistente.

*   **RF-07 Sistema de Alertas:** El sistema debe notificar al usuario sobre eventos críticos (stock mínimo alcanzado, mantenimientos vencidos, labores bloqueadas por clima, lotes listos para cosecha).
    *   **Criterios de aceptación:**
        1. Dado un insumo con `stock_actual <= stock_minimo_alerta`, entonces se genera una alerta de stock crítico visible para ADMIN y AGRONOMO.
        2. Dada una máquina con `horas_uso_actuales >= horas_para_proximo_service`, entonces se genera una alerta de mantenimiento.
        3. Dada una labor que pasa a `BLOQUEADA_CLIMA`, entonces se notifica al usuario que la programó.
        4. Dado un lote que permanece más de 15 días en `LISTO_COSECHA`, entonces se genera una alerta de cosecha demorada.
        5. Dada una alerta ya generada y no resuelta, entonces no se duplica al volver a evaluarse la condición.

*   **RF-08 Tablero Financiero (Dashboard):** El sistema debe calcular y mostrar métricas de rendimiento y rentabilidad, incluyendo ingresos brutos, costos totales acumulados y Retorno de Inversión (ROI) por lote.
    *   **Criterios de aceptación:**
        1. Dado un lote con cosecha registrada, entonces el tablero muestra ingreso bruto, costos acumulados, margen neto y `ROI (%) = Margen Neto / Costos Totales Acumulados × 100`.
        2. Dado un lote sin cosecha registrada, entonces se muestran sólo los costos acumulados y el ROI figura como "pendiente".
        3. Dado un lote con costos acumulados igual a 0, entonces el ROI no se calcula (se evita la división por cero) y se informa "sin costos registrados".
        4. Dado el tablero con hasta 500 lotes, entonces la respuesta no supera los 2 segundos (RNF-02).

## 5.3. Requerimientos No Funcionales (RNF)

| ID | Requerimiento | Prioridad | Criterio de aceptación (medible) |
| :--- | :--- | :--- | :--- |
| RNF-01 | Seguridad y Auditoría | Must | 100% de las contraseñas almacenadas con BCrypt (factor ≥ 10). Toda operación de escritura sobre compras, labores, mantenimientos, cosechas y stock persiste el `usuario_id` responsable. |
| RNF-02 | Rendimiento | Should | El percentil 95 de las consultas al tablero financiero responde en menos de 2 segundos con 500 lotes cargados. |
| RNF-03 | Disponibilidad | Should | Disponibilidad mensual objetivo ≥ 99%. Una caída de la API climática no deja fuera de servicio a ningún otro módulo. |
| RNF-04 | Compatibilidad Tecnológica | Must | El mismo `schema.sql` se ejecuta sin errores en H2 (perfil `dev`) y en PostgreSQL (perfil `prod`). |
| RNF-05 | Usabilidad | Could | Las pantallas de iniciar labor y consultar alertas se usan sin scroll horizontal en un ancho de 360 px. |
| RNF-06 | Observabilidad | Could | El backend expone *health check* y métricas (Spring Boot Actuator) y registra logs estructurados con un identificador de petición (ver doc 09). |

*   **RNF-01 Seguridad y Auditoría:** Las contraseñas deben almacenarse encriptadas (ej. BCrypt). Toda acción que modifique el estado del sistema (compras, labores, mantenimientos) debe auditarse registrando el ID del usuario responsable.
*   **RNF-02 Rendimiento:** El tiempo de respuesta para las consultas al tablero financiero no debe superar los 2 segundos, utilizando campos desnormalizados persistidos para optimizar los cálculos.
*   **RNF-03 Disponibilidad:** El sistema web debe estar disponible 24/7, con un diseño que contemple tolerancia a fallos en la conexión a la API climática (ver doc 07, sección 7.3).
*   **RNF-04 Compatibilidad Tecnológica:** El sistema debe soportar bases de datos PostgreSQL para entornos de producción (soportando proyecciones GIS futuras) y H2 en memoria para desarrollo, abstrayendo temporalmente los datos geográficos a formato WKT.
*   **RNF-05 Usabilidad:** La interfaz debe ser responsive (adaptable a dispositivos móviles) dado que los agrónomos operarán ocasionalmente desde el campo.
*   **RNF-06 Observabilidad:** El sistema debe permitir conocer su estado de salud y detectar fallas (por ejemplo, de la API climática) sin necesidad de revisar la base de datos manualmente.
