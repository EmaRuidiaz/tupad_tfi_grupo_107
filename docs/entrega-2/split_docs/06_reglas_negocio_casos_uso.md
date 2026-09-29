# 6. Reglas de Negocio y Casos de Uso

## 6.1. Reglas de Negocio (RN)

Las siguientes reglas dictan las restricciones lógicas y comerciales operativas del sistema AgTech:

*   **RN-01 Validación Climática:** Una labor agrícola sensible (como pulverización o siembra) no puede pasar a estado `EJECUTADA` si las variables meteorológicas exceden los umbrales de seguridad (ej. vientos fuertes o humedad por debajo de lo requerido). Pasará a estado `BLOQUEADA_CLIMA`.
*   **RN-02 Restricción de Cosecha:** Un lote no puede registrar una cosecha a menos que su estado agronómico actual sea `LISTO_COSECHA`.
*   **RN-03 Consistencia de Inventario:** No es posible iniciar una labor agrícola si el `stock_actual` de los insumos requeridos es menor a la cantidad demandada por la labor.
*   **RN-04 Trazabilidad de Responsabilidad:** Absolutamente todas las labores ejecutadas, mantenimientos realizados y movimientos de stock (compras o ajustes) deben quedar vinculadas de forma inmutable al `usuario_id` que las autorizó.
*   **RN-05 Costeo Inmutable:** Una vez que una labor finaliza y se calcula su `costo_total_calculado`, este valor se vuelve histórico y no debe recalcularse retrospectivamente aunque el precio unitario del insumo cambie en el futuro.
*   **RN-06 Mantenimiento Obligatorio:** Si las horas de uso de una maquinaria superan las `horas_para_proximo_service`, la maquinaria generará una alerta de mantenimiento crítico, debiendo registrarse un servicio para reiniciar el contador.

## 6.2. Casos de Uso (CU) Principales

A continuación se listan los Casos de Uso más representativos del sistema:

### CU-01: Iniciar Sesión (Login)
*   **Actor:** Usuario (Admin, Agrónomo, Operario).
*   **Descripción:** El usuario ingresa sus credenciales. El sistema las valida y devuelve un token JWT con los permisos asociados a su rol.

### CU-02: Registrar Compra de Insumos
*   **Actor:** Admin.
*   **Descripción:** El actor registra una factura de un proveedor, especificando los insumos y las cantidades. El sistema actualiza el `stock_actual` y crea registros en `movimiento_stock` con tipo `INGRESO_COMPRA`.

### CU-03: Programar Labor Agrícola
*   **Actor:** Agrónomo.
*   **Descripción:** El actor asigna una labor a un lote, especificando la maquinaria a usar, los insumos y las fechas previstas. El sistema verifica el stock y reserva lógicamente los insumos.

### CU-04: Ejecutar Labor (Validación Climática)
*   **Actor:** Operario / Agrónomo.
*   **Descripción:** El actor marca una labor programada como iniciada. El sistema consulta la API climática, aplica las Reglas de Negocio (RN-01) y, si se aprueba, descuenta el stock físico (RN-03) y comienza a contabilizar las horas de máquina.

### CU-05: Registrar Cosecha y Consultar ROI
*   **Actor:** Agrónomo / Admin.
*   **Descripción:** Al finalizar el ciclo del cultivo, se registra el rendimiento en toneladas. El sistema utiliza los ingresos generados y los costos históricos de las labores (RN-05) para presentar el margen neto y el ROI final del lote en el Tablero de Control.
