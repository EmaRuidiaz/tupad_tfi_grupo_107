# Sistema AgTech de Gestión y Optimización de Lotes de Cultivo

**Trabajo Final Integrador (TFI) — Grupo 107**

* **Integrantes:**
  * Ruidiaz, Emanuel Facundo
  * Roques Zeballos, Juan Martín
  * Santini, Mauro Gonzalo
* **Tutor:** Prof. Herrera Molas, Gerardo A.

---

## Resumen Ejecutivo
El presente proyecto consiste en el desarrollo de una plataforma integral **AgTech** orientada a optimizar la gestión operativa, el control de recursos y la toma de decisiones en el sector agropecuario. La aplicación resuelve problemáticas críticas como la descoordinación frente a condiciones climáticas adversas, el desgaste imprevisto de maquinaria y la falta de visibilidad financiera por lote. Para lograrlo, combina la administración completa de inventarios, lotes y vehículos mediante operaciones ABM (CRUD), un asistente de planificación inteligente basado en APIs de clima y geolocalización, y un módulo analítico de costos y mantenimiento.

## 1. Definición de la Problemática
En el sector agropecuario actual, la eficiencia operativa y la toma de decisiones basada en datos son factores críticos para la rentabilidad y la sostenibilidad. Los productores y administradores agrícolas enfrentan habitualmente los siguientes desafíos:

* **Descoordinación climática y operativa:** La ejecución de tareas críticas (como fumigación, fertilización o cosecha) bajo condiciones meteorológicas inadecuadas genera pérdidas significativas de insumos, menor eficacia en los tratamientos y riesgos ambientales.
* **Desgaste imprevisto de maquinaria:** La falta de un seguimiento preciso de las horas de uso y el esfuerzo operativo de tractores y vehículos agrícolas conduce a roturas imprevistas, costos de reparación elevados y tiempos improductivos ("downtime").
* **Falta de visibilidad financiera y de inventarios:** La gestión desconectada del stock de semillas, el consumo de combustible por hectárea y los costos asociados dificultan la medición precisa del Retorno de Inversión (ROI) por lote de cultivo.

## 2. Solución Planeada y Módulos de Gestión (ABM)
Se propone el desarrollo de una plataforma que centraliza la administración agrícola a través de operaciones ABM (Altas, Bajas y Modificaciones) para sus entidades principales, incorporando dos diferenciadores clave:

* **Módulos de Gestión (ABM):**
  * **Gestión de Lotes:** Creación y administración de parcelas y sus límites geográficos.
  * **Inventario de Semillas e Insumos:** Control de stock y disponibilidad de insumos agrícolas.
  * **Maquinaria y Vehículos:** Registro de tractores, cosechadoras y vehículos, junto con sus estados operativos.
  * **Registro de Cosechas:** Trazabilidad de los rendimientos obtenidos por lote.
* **Asistente de Planificación Inteligente con Clima y Geolocalización:** Un motor de reglas que evalúa automáticamente la viabilidad de programar tareas agrícolas cruzando la ubicación del lote con datos meteorológicos en tiempo real para bloquear tareas si las condiciones no son óptimas.
* **Módulo de Mantenimiento Predictivo y Control de Costos:** Sistema que registra el desgaste de la maquinaria por horas de trabajo y calcula los costos operativos (combustible, insumos), ofreciendo un tablero financiero por hectárea y alertas de service.

## 3. Stack Tecnológico
Para el desarrollo de la aplicación se utilizará el siguiente stack tecnológico principal:

| Capa / Componente | Tecnología | Detalle de Uso |
| :--- | :--- | :--- |
| **Backend** | Java Spring Boot | Spring Data JPA, API REST, WebClient / RestClient |
| **Base de Datos (Dev)** | H2 Database | Base embebida para desarrollo local ágil y pruebas |
| **Base de Datos (Prod)** | PostgreSQL | Persistencia relacional robusta desplegada en la nube |
| **Frontend** | React | Interfaz de usuario dinámica y tableros interactivos |

## 4. Estrategia de Persistencia y Despliegue (Spring Profiles)
Con el objetivo de desacoplar el modelo de negocio de la infraestructura subyacente y aplicar buenas prácticas de ingeniería de software, se adopta un esquema multientorno gestionado mediante **Spring Profiles**:

* **Perfil de Desarrollo (`dev`):** Utiliza **H2 Database**. Permite a cualquier miembro del equipo de desarrollo o docente evaluador clonar y ejecutar el proyecto de forma inmediata y autocontenida (`mvn spring-boot:run`), sin necesidad de instalar o configurar servidores locales adicionales.
* **Perfil de Producción / Despliegue (`prod`):** Se conecta a una instancia de **PostgreSQL** alojada en la nube. Proporciona persistencia permanente, soporte para concurrencia y solidez en el manejo de las entidades relacionales del sector agropecuario, cumpliendo además con el requerimiento de contar con un servicio desplegado en la nube.

## 5. Estructura del Repositorio Único
De acuerdo con las normativas de la cátedra para el desarrollo centralizado del proyecto:

```text
tupad_tfi_grupo_107/
├── backend/            # API REST en Java Spring Boot
├── frontend/           # SPA en React
├── database/           # Scripts DDL, DML (seed) y diagramas ER
├── docs/               # Informes de entregas, documentación técnica y diseño
├── .gitignore          # Archivos y configuraciones excluidas del repositorio
└── README.md           # Documentación principal del proyecto
```

