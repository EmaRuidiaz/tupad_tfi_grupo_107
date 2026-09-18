# Product Backlog - Tablero Ágil (Kanban)
**Proyecto:** Sistema AgTech de Gestión de Lotes
**Herramientas recomendadas:** GitHub Projects o Trello.

---

## 🏗️ Épica 0: Setup y Arquitectura Base
*(Tareas iniciales para que el equipo pueda empezar a programar)*

* **HU-01: Inicialización del Repositorio y Backend**
  * **Descripción:** Como desarrollador, quiero tener el proyecto Spring Boot base configurado con los perfiles H2/PostgreSQL y las dependencias (JPA, Web) para poder crear los primeros endpoints.
  * **Criterios de Aceptación:** Proyecto en carpeta `/backend` compilando. Conexión a H2 exitosa.

* **HU-02: Inicialización del Frontend**
  * **Descripción:** Como desarrollador frontend, quiero tener el proyecto React (Vite) inicializado con un enrutador (React Router) y una estructura base, para poder maquetar las primeras pantallas.
  * **Criterios de Aceptación:** Proyecto en carpeta `/frontend` compilando. Navbar o menú lateral básico funcionando.

---

## 🗺️ Épica 1: Catastro e Inventario
*(Ideal para que 2 integrantes trabajen en paralelo: uno en Lotes y otro en Insumos)*

* **HU-03: Gestión de Lotes (CRUD)**
  * **Como** Ingeniero Agrónomo
  * **Quiero** poder registrar, editar y listar los lotes agrícolas con su superficie y coordenadas.
  * **Para** tener un catastro digital de la superficie sembrable.
  * **Criterios de Aceptación:** API REST de lotes funcionando. Pantalla de listado y formulario de creación en React. 

* **HU-04: Gestión de Inventario (CRUD y Alertas)**
  * **Como** Administrador
  * **Quiero** gestionar el catálogo de insumos y definir un stock mínimo.
  * **Para** controlar las existencias y los costos unitarios.
  * **Criterios de Aceptación:** API REST de insumos. Pantalla en React con listado. Indicador visual si `stock_actual <= stock_minimo_alerta`.

---

## 🚜 Épica 2: Maquinaria y Mantenimiento

* **HU-05: Registro de Maquinaria y Horómetros**
  * **Como** Administrador
  * **Quiero** registrar tractores y cosechadoras con sus horómetros actuales y costo por hora.
  * **Para** llevar un control del desgaste y los costos operativos.
  * **Criterios de Aceptación:** ABM completo de maquinaria (Backend + Frontend).

* **HU-06: Trazabilidad de Mantenimientos**
  * **Como** Operador de Campo
  * **Quiero** registrar el service realizado a una máquina.
  * **Para** llevar el historial de gastos mecánicos y establecer el próximo umbral de service.
  * **Criterios de Aceptación:** Formulario para agregar un mantenimiento a una máquina. Al guardar, debe actualizarse automáticamente el campo `horas_para_proximo_service` de la máquina.

---

## 🌤️ Épica 3: Asistente Climático Inteligente

* **HU-07: Motor de Reglas Agronómicas**
  * **Como** Ingeniero Agrónomo
  * **Quiero** definir parámetros climáticos máximos y mínimos por tipo de labor (ej. viento máximo para fumigar).
  * **Para** que el sistema sepa cuándo restringir una tarea.
  * **Criterios de Aceptación:** ABM de `regla_climatica` (Backend + Frontend).

* **HU-08: Integración API Meteorológica**
  * **Como** Sistema
  * **Quiero** consumir una API externa (ej. Open-Meteo) enviando latitud y longitud.
  * **Para** obtener las condiciones climáticas en tiempo real de un lote específico.
  * **Criterios de Aceptación:** Servicio en Spring Boot (WebClient/RestClient) que retorne la temperatura, viento y lluvia actual dado un `lote_id`.

---

## 📋 Épica 4: Labores Agrícolas (Core del Negocio)

* **HU-09: Planificación de Órdenes de Trabajo**
  * **Como** Agrónomo
  * **Quiero** crear una labor asignando un lote, una máquina y los insumos a consumir.
  * **Para** dejar programada la orden de trabajo de la semana.
  * **Criterios de Aceptación:** Formulario complejo en React (selección de lote, máquina y grilla dinámica para agregar insumos). API que guarde la labor en estado `PLANIFICADA`.

* **HU-10: Autorización Climática de Labor**
  * **Como** Agrónomo
  * **Quiero** que al intentar iniciar una labor, el sistema valide el clima actual contra las reglas.
  * **Para** bloquear la tarea si hay riesgo ambiental o ineficacia.
  * **Criterios de Aceptación:** Al presionar "Validar Clima", el backend cruza los datos de HU-07 y HU-08. Si no cumple, cambia el estado a `BLOQUEADA_CLIMA` y muestra el motivo.

* **HU-11: Ejecución y Cierre de Labor (Impacto de stock y horas)**
  * **Como** Operador
  * **Quiero** marcar una labor como `COMPLETADA` indicando las horas reales de máquina empleadas.
  * **Para** registrar el trabajo físico.
  * **Criterios de Aceptación:** Al completarse, el backend DEBE: 1) Descontar el stock de los insumos usados. 2) Sumar las horas a la maquinaria. 3) Calcular y guardar el costo total de la labor.

---

## 📈 Épica 5: Cosechas y Tablero Financiero

* **HU-12: Registro de Cosecha**
  * **Como** Administrador
  * **Quiero** registrar las toneladas recolectadas y el precio de venta en un lote.
  * **Para** calcular el rendimiento por hectárea y el ingreso bruto.
  * **Criterios de Aceptación:** Formulario de cosecha. Cálculo automático de `rinde_ton_por_ha` según superficie del lote.

* **HU-13: Tablero de ROI / Margen Neto**
  * **Como** Dueño
  * **Quiero** ver un reporte por lote de Ingresos vs. Costos Acumulados (labores previas).
  * **Para** conocer la rentabilidad final de la campaña.
  * **Criterios de Aceptación:** Endpoint que sume todos los `costo_total_calculado` de las labores de un lote y lo reste al ingreso de la cosecha. Gráfico o tabla resumen en React.
