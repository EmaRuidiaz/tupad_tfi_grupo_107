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


## 8.3. Diagrama de Secuencia (Validación Climática)

Este diagrama ilustra el flujo de comunicación dinámico entre los componentes del sistema (Frontend, Backend, Base de Datos y Servicios Externos) durante la ejecución del Caso de Uso CU-04. Demuestra cómo se aplican las reglas de negocio en tiempo real.

```mermaid
sequenceDiagram
    actor Agro as Agrónomo / Operario
    participant UI as Frontend (React)
    participant API as Backend (Spring Boot)
    participant Ext as API Climática
    participant DB as Base de Datos (PostgreSQL/H2)

    Agro->>UI: Clic en "Iniciar Labor"
    UI->>API: POST /api/labores/{id}/iniciar (Envia JWT)
    
    API->>DB: Obtener datos del lote (coordenadas) y labor
    DB-->>API: Datos del lote y labor retornados
    
    API->>Ext: GET clima actual (latitud, longitud)
    Ext-->>API: JSON con (temperatura, viento, humedad)
    
    API->>DB: Consultar regla_climatica para el tipo_labor
    DB-->>API: Umbrales climáticos máximos/mínimos
    
    alt Clima Óptimo (Regla Cumplida)
        API->>DB: Insertar movimiento_stock (Descontar Insumos)
        API->>DB: UPDATE labor_agricola (estado='EN_EJECUCION')
        API-->>UI: 200 OK (Labor Iniciada con éxito)
        UI-->>Agro: Muestra mensaje de éxito y cronómetro de labor
    else Clima Adverso (Regla Incumplida)
        API->>DB: UPDATE labor_agricola (estado='BLOQUEADA_CLIMA')
        API-->>UI: 409 Conflict (Alerta Climática)
        UI-->>Agro: Muestra alerta de reprogramación por mal clima
    end
```
