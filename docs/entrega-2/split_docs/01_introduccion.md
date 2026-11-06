# 1. Introducción y Alcance de la Entrega

**Proyecto:** Sistema AgTech de Gestión y Optimización de Lotes de Cultivo  
**Cátedra:** Trabajo Final Integrador (TFI) — Grupo 107  
**Integrantes:**
* Ruidiaz, Emanuel Facundo
* Roques Zeballos, Juan Martín
* Santini, Mauro Gonzalo

**Tutor:** Prof. Herrera Molas, Gerardo A.  

El presente documento constituye la **2.ª Entrega** exigida por la cátedra para la obtención de la **Condición de Regular**. En él se formaliza el diseño del sistema con las mejoras incorporadas tras las sucesivas instancias de revisión docente (*Feedback*):

1. **Esquema de Base de Datos Relacional (DER)** actualizado con módulos de Usuarios, Compras de Insumos e Historial de Stock, y soporte GIS/Polígonos.
2. **Diccionario de Datos** detallando tipos de datos, restricciones explícitas de integridad referencial, unicidad, y checks agronómicos.
3. **Listado de Módulos a Desarrollar** con la formalización del cálculo de ROI y especificaciones ampliadas de seguridad y alertas.

En respuesta a la **revisión integral de la segunda entrega**, se incorporaron además:
* **Criterios de aceptación y priorización (MoSCoW)** para todos los requerimientos funcionales, y criterios medibles para los no funcionales (doc 05).
* **Diccionario de datos completo** de las 14 tablas del modelo físico y **reglas de actualización de los campos calculados** (doc 03).
* **Reserva de stock** en el modelo de datos (`insumo.stock_reservado` y `labor_insumo.estado_reserva`), coherente con el CU-03 (docs 02 y 03, `database/schema.sql`).
* **Formalización de los estados de negocio** y de todas sus transiciones, nuevas reglas de negocio y casos de uso (doc 06).
* **Matriz de permisos por rol**, **estrategia de resiliencia ante la caída de la API climática** y **matriz de trazabilidad RF-RN-RNF-CU-Módulo-Entidades** (doc 07).
* **Diagramas UML** de estado, clases, secuencia, actividad y despliegue (doc 08).
* **Métricas operativas, monitoreo y observabilidad** (doc 09).

En respuesta a la **tercera revisión docente**, se completaron y formalizaron los siguientes puntos:
* **Formalización del concepto de Campaña Agrícola / Ciclo Productivo** y **política de cosechas e historial plurianual** (cosecha consolidada por ciclo productivo y soporte de múltiples campañas consecutivas por lote, docs 02, 03, 04 y 06 — `RN-12`).
* **Estrategia de concurrencia para la gestión de stock** mediante transacciones ACID (`READ_COMMITTED`), bloqueo pesimista a nivel de fila (`SELECT ... FOR UPDATE`), ordenamiento determinista de bloqueos por `insumo_id` para prevención de *deadlocks* y restricciones `CHECK` en base de datos (docs 02, 03, 06 y 07 — `RN-13`).
* **Refinamiento de multiplicidades en los diagramas UML y DER**, explicitando cardinalidades exactas (`1`, `0..1`, `0..*`, `1..*`) en todas las asociaciones y dependencias del modelo de dominio (docs 02 y 08).
* **Definición formal del cálculo de disponibilidad del sistema (SLA)** y **estrategia de respaldo y recuperación de datos (Backup, RPO y RTO)** (docs 05 y 09).

Para facilitar la lectura, esta entrega se ha dividido en los siguientes documentos:
- [01_introduccion.md](./01_introduccion.md) (este documento)
- [02_modelo_der.md](./02_modelo_der.md)
- [03_diccionario_datos.md](./03_diccionario_datos.md)
- [04_modulos.md](./04_modulos.md)
- [05_requerimientos.md](./05_requerimientos.md)
- [06_reglas_negocio_casos_uso.md](./06_reglas_negocio_casos_uso.md)
- [07_arquitectura_trazabilidad.md](./07_arquitectura_trazabilidad.md)
- [08_diagramas_complementarios.md](./08_diagramas_complementarios.md)
- [09_operacion_monitoreo.md](./09_operacion_monitoreo.md)
