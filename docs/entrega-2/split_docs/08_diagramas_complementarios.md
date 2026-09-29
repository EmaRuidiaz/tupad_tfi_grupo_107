# 8. Diagramas Complementarios

## 8.1. Diagrama de Arquitectura (N-Capas)

El siguiente diagrama detalla la arquitectura de alto nivel y la separación de responsabilidades:

```mermaid
flowchart TD
    subgraph Frontend ["Capa de Presentación (Cliente)"]
        UI[Web App / Mobile App]
    end

    subgraph Backend ["Capa de Backend (Spring Boot)"]
        SEC[Spring Security & JWT]
        CTRL[REST Controllers]
        SERV[Service Layer - Reglas de Negocio]
        JPA[Spring Data JPA - Repositories]
    end

    subgraph DataBase ["Capa de Datos"]
        DB[(PostgreSQL / H2)]
    end

    subgraph Externo ["Servicios Externos"]
        API[API Climática Externa]
    end

    UI -- HTTP/REST --> SEC
    SEC -- Valida Token --> CTRL
    CTRL -- Delega Lógica --> SERV
    SERV -- Consulta Clima --> API
    SERV -- Persistencia --> JPA
    JPA -- SQL --> DB
```

## 8.2. Diagrama de Casos de Uso

Se visualizan las interacciones principales de los actores del sistema con los módulos de la aplicación:

```mermaid
flowchart LR
    Admin((Administrador))
    Agro((Agrónomo))
    Op((Operario))

    subgraph AgTech ["Sistema AgTech"]
        CU1[Iniciar Sesión]
        CU2[Registrar Compras e Inventario]
        CU3[Gestionar Lotes y Cultivos]
        CU4[Programar Labores]
        CU5[Ejecutar Labores y Clima]
        CU6[Registrar Cosechas y ROI]
        CU7[Mantenimiento de Maquinaria]
    end

    Admin --> CU1
    Agro --> CU1
    Op --> CU1

    Admin --> CU2
    Admin --> CU3
    Admin --> CU6

    Agro --> CU3
    Agro --> CU4
    Agro --> CU5
    Agro --> CU6
    Agro --> CU7

    Op --> CU5
    Op --> CU7
```
