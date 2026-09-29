# 5. Requerimientos del Sistema

## 5.1. Requerimientos Funcionales (RF)

*   **RF-01 Gestión de Usuarios y Seguridad:** El sistema debe permitir la autenticación de usuarios mediante credenciales seguras (JWT), asignando roles específicos (ADMIN, AGRONOMO, OPERARIO) que delimiten el acceso a las funciones.
*   **RF-02 Gestión de Lotes (Catastro):** El sistema debe permitir el alta, baja, y modificación (ABM) de lotes agrícolas, incluyendo sus coordenadas geográficas (polígonos) y características agronómicas.
*   **RF-03 Gestión de Inventario y Compras:** El sistema debe registrar las compras a proveedores, actualizando automáticamente el stock de insumos y registrando un historial detallado de movimientos.
*   **RF-04 Gestión de Maquinaria:** El sistema debe administrar el parque de maquinaria, registrar las horas de uso y programar/registrar servicios de mantenimiento.
*   **RF-05 Gestión de Labores Agrícolas:** El sistema debe permitir programar, ejecutar y finalizar labores agrícolas en los lotes, descontando insumos del stock y calculando automáticamente el costo total en base a insumos y uso de maquinaria.
*   **RF-06 Asistente Climático:** El sistema debe verificar las condiciones climáticas (API externa) antes de permitir la ejecución de ciertas labores, bloqueando aquellas en condiciones adversas (ej. ráfagas fuertes, humedad baja).
*   **RF-07 Sistema de Alertas:** El sistema debe notificar al usuario sobre eventos críticos (stock mínimo alcanzado, mantenimientos vencidos, labores bloqueadas por clima, lotes listos para cosecha).
*   **RF-08 Tablero Financiero (Dashboard):** El sistema debe calcular y mostrar métricas de rendimiento y rentabilidad, incluyendo ingresos brutos, costos totales acumulados y Retorno de Inversión (ROI) por lote.

## 5.2. Requerimientos No Funcionales (RNF)

*   **RNF-01 Seguridad y Auditoría:** Las contraseñas deben almacenarse encriptadas (ej. BCrypt). Toda acción que modifique el estado del sistema (compras, labores, mantenimientos) debe auditarse registrando el ID del usuario responsable.
*   **RNF-02 Rendimiento:** El tiempo de respuesta para las consultas al tablero financiero no debe superar los 2 segundos, utilizando campos desnormalizados persistidos para optimizar los cálculos.
*   **RNF-03 Disponibilidad:** El sistema web debe estar disponible 24/7, con un diseño que contemple tolerancia a fallos en la conexión a la API climática.
*   **RNF-04 Compatibilidad Tecnológica:** El sistema debe soportar bases de datos PostgreSQL para entornos de producción (soportando proyecciones GIS futuras) y H2 en memoria para desarrollo, abstrayendo temporalmente los datos geográficos a formato WKT.
*   **RNF-05 Usabilidad:** La interfaz debe ser responsive (adaptable a dispositivos móviles) dado que los agrónomos operarán ocasionalmente desde el campo.
