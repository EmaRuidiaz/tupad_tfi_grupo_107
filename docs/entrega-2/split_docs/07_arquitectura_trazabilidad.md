# 7. Arquitectura Detallada y Trazabilidad

## 7.1. Arquitectura Detallada

El sistema se ha diseñado bajo una **Arquitectura de Capas (N-Tier)** enfocada en el paradigma de API REST, promoviendo alta cohesión y bajo acoplamiento.

### Componentes de la Arquitectura:

1.  **Capa de Presentación (Frontend):**
    *   **Tecnología:** (A definir en etapas posteriores, ej. React, Angular o vistas genéricas).
    *   **Responsabilidad:** Interfaz de usuario (UI), visualización de los tableros de ROI, formularios y manejo del token JWT en el cliente.
2.  **Capa de Servicios y Negocio (Backend):**
    *   **Tecnología:** Java 17+ con Spring Boot 3.x.
    *   **Componentes Principales:**
        *   **Controllers (API REST):** Exponen los endpoints (ej. `/api/lotes`, `/api/labores`).
        *   **Services:** Contienen la lógica pura, implementando las Reglas de Negocio (validación climática, costeo).
        *   **Security (Spring Security + JWT):** Intercepta peticiones para asegurar que solo usuarios autorizados (según su rol) ejecuten endpoints críticos.
3.  **Capa de Acceso a Datos (Persistencia):**
    *   **Tecnología:** Spring Data JPA (Hibernate).
    *   **Responsabilidad:** Abstracción y mapeo objeto-relacional (ORM) de las entidades del DER.
4.  **Capa de Base de Datos:**
    *   **Tecnología:** H2 (Entorno Local/Testing) / PostgreSQL (Entorno Producción).
    *   **Responsabilidad:** Almacenamiento seguro, garantizando integridad referencial, restricciones (CHECKs) y soporte WKT para coordenadas y polígonos.
5.  **Integraciones Externas:**
    *   **API Climática:** Servicio de terceros consultado por el backend para obtener variables meteorológicas en tiempo real basadas en la latitud/longitud del lote.

## 7.2. Matriz de Trazabilidad

Esta matriz relaciona los Requerimientos Funcionales (RF), los Casos de Uso (CU) y los Módulos definidos en el diseño, asegurando cobertura total.

| Requerimiento Funcional (RF) | Caso de Uso (CU) Relacionado | Módulo Relacionado (Ver doc 04_modulos) |
| :--- | :--- | :--- |
| **RF-01** (Usuarios y Seguridad) | CU-01 (Iniciar Sesión) | Módulo 1 (Seguridad y Autenticación) |
| **RF-02** (Lotes y Catastro) | CU-02 (Registrar Lote) | Módulo 2 (Catastro y GIS) |
| **RF-03** (Inventario y Compras) | CU-02 (Registrar Compra) | Módulo 3 y Módulo 4 (Inventario) |
| **RF-04** (Maquinaria) | *CU-06* (Registrar Service) | Módulo 5 (Parque de Maquinaria) |
| **RF-05** (Labores Agrícolas) | CU-03, CU-04 (Prog/Ejec Labor) | Módulo 7 (Gestión de Labores) |
| **RF-06** (Asistente Climático) | CU-04 (Ejecutar Labor) | Módulo 6 (Asistente Inteligente) |
| **RF-07** (Alertas) | *Variados* (Stock, Clima, etc) | Módulo 8 (Sistema de Alertas) |
| **RF-08** (Tablero y ROI) | CU-05 (Cosecha y ROI) | Módulo 9 (Tablero Financiero) |
