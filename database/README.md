# Módulo de Base de Datos

En este directorio se almacenan los esquemas, scripts DDL/DML y el modelo de persistencia del proyecto:

* [**`schema.sql`**](schema.sql): Definición DDL estándar de las 8 tablas relacionales, claves primarias (`IDENTITY`), claves foráneas con cascada/restricción, constraints `CHECK` e índices. Compatible tanto con **H2 Database** (`dev`) como con **PostgreSQL** (`prod`).
* [**`data.sql`**](data.sql): Datos semilla (seed / fixtures) realistas para pruebas inmediatas y demostración en la defensa oral (lotes pampeanos, catálogo de insumos, maquinaria con horómetros y reglas climáticas).
* Para consultar el **Diagrama Entidad-Relación (DER)** y el **Diccionario de Datos** completo, ver la [documentación de la 2.ª Entrega](../docs/entrega-2/diseno_y_modulos.md).