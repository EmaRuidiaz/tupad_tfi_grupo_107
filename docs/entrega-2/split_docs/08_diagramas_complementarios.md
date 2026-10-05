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
        CU8[Cancelar / Reprogramar Labores]
        CU9[Consultar Alertas]
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

    Agro --> CU8
    Agro --> CU9

    Admin --> CU9

    Op --> CU5
    Op --> CU7
    Op --> CU9
```

*Correspondencia con los casos de uso del doc 06:* CU1 → CU-01 · CU2 → CU-02 · CU3 → CU-07 · CU4 → CU-03 · CU5 → CU-04 · CU6 → CU-05 · CU7 → CU-06 · CU8 → CU-08 · CU9 → CU-09.


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
    Ext-->>API: JSON con (temperatura, viento, humedad, prob. lluvia)
    Note over API,Ext: Timeout 3 s, retry y circuit breaker.<br/>Si falla, ver diagrama 8.6
    
    API->>DB: Consultar regla_climatica para el tipo_labor
    DB-->>API: Umbrales climáticos máximos/mínimos
    
    alt Clima Óptimo (Regla Cumplida)
        API->>DB: UPDATE labor_agricola (estado='APROBADA_CLIMA', datos climáticos)
        API->>DB: UPDATE insumo (stock_actual, stock_reservado) y labor_insumo (CONSUMIDO)
        API->>DB: Insertar movimiento_stock (EGRESO_LABOR)
        API->>DB: UPDATE labor_agricola (estado='EN_EJECUCION') y maquinaria (EN_LABOR)
        API-->>UI: 200 OK (Labor Iniciada con éxito)
        UI-->>Agro: Muestra mensaje de éxito y cronómetro de labor
    else Clima Adverso (Regla Incumplida)
        API->>DB: UPDATE labor_agricola (estado='BLOQUEADA_CLIMA')
        API-->>UI: 409 Conflict (Alerta Climática)
        UI-->>Agro: Muestra alerta de reprogramación por mal clima
    end
```

## 8.4. Diagramas de Estado

Representan gráficamente las transiciones formalizadas en [06_reglas_negocio_casos_uso.md](./06_reglas_negocio_casos_uso.md#63-estados-de-negocio-y-transiciones) (RN-08).

### 8.4.1. Lote

```mermaid
stateDiagram-v2
    [*] --> EN_PREPARACION : Alta del lote
    EN_PREPARACION --> SEMBRADO : Labor SIEMBRA completada
    SEMBRADO --> EN_CRECIMIENTO : Emergencia registrada
    EN_CRECIMIENTO --> LISTO_COSECHA : Madurez comercial
    LISTO_COSECHA --> COSECHADO : Cosecha registrada (CU-05)
    COSECHADO --> EN_PREPARACION : Nueva campaña
    COSECHADO --> DESCANSO : Sin siembra planificada
    DESCANSO --> EN_PREPARACION : Se retoma la actividad
    SEMBRADO --> EN_PREPARACION : Pérdida total
    EN_CRECIMIENTO --> EN_PREPARACION : Pérdida total
```

### 8.4.2. Labor Agrícola

```mermaid
stateDiagram-v2
    [*] --> PLANIFICADA : Programar (reserva insumos)
    PLANIFICADA --> APROBADA_CLIMA : Clima OK / autorización manual
    PLANIFICADA --> BLOQUEADA_CLIMA : Clima adverso
    PLANIFICADA --> EN_EJECUCION : Inicio sin regla climática (LABRANZA)
    BLOQUEADA_CLIMA --> APROBADA_CLIMA : Revalidación OK
    BLOQUEADA_CLIMA --> BLOQUEADA_CLIMA : Revalidación fallida
    BLOQUEADA_CLIMA --> PLANIFICADA : Reprogramar
    APROBADA_CLIMA --> EN_EJECUCION : Iniciar (consume stock)
    APROBADA_CLIMA --> PLANIFICADA : Vence ventana de 2 h
    EN_EJECUCION --> COMPLETADA : Fin de labor (calcula costo)
    PLANIFICADA --> CANCELADA : Cancelar (libera reservas)
    APROBADA_CLIMA --> CANCELADA : Cancelar
    BLOQUEADA_CLIMA --> CANCELADA : Cancelar
    COMPLETADA --> [*]
    CANCELADA --> [*]
```

### 8.4.3. Maquinaria

```mermaid
stateDiagram-v2
    [*] --> OPERATIVO : Alta
    OPERATIVO --> EN_LABOR : Labor EN_EJECUCION
    EN_LABOR --> OPERATIVO : Labor COMPLETADA
    OPERATIVO --> EN_MANTENIMIENTO : Ingreso a taller
    EN_MANTENIMIENTO --> OPERATIVO : Service registrado (CU-06)
    OPERATIVO --> FUERA_DE_SERVICIO : Rotura grave / baja
    EN_MANTENIMIENTO --> FUERA_DE_SERVICIO : Reparación no viable
    FUERA_DE_SERVICIO --> EN_MANTENIMIENTO : Se decide reparar
```

### 8.4.4. Reserva de Insumo (`labor_insumo`)

```mermaid
stateDiagram-v2
    [*] --> RESERVADO : Programar labor
    RESERVADO --> CONSUMIDO : Labor EN_EJECUCION
    RESERVADO --> LIBERADO : Labor CANCELADA
    CONSUMIDO --> [*]
    LIBERADO --> [*]
```

## 8.5. Diagrama de Clases del Dominio

Modelo de clases de las entidades JPA y sus principales responsabilidades. Los métodos de transición encapsulan las reglas de la sección 6.3, de modo que un estado inválido no pueda asignarse desde fuera de la entidad.

```mermaid
classDiagram
    direction LR

    class Usuario {
        Long id
        String username
        String passwordHash
        Rol rol
        String email
    }
    class Lote {
        Long id
        String nombre
        BigDecimal superficieHa
        BigDecimal latitud
        BigDecimal longitud
        String poligonoWkt
        EstadoLote estado
        +cambiarEstado(EstadoLote nuevo)
    }
    class Insumo {
        Long id
        String nombre
        CategoriaInsumo categoria
        UnidadMedida unidadMedida
        BigDecimal stockActual
        BigDecimal stockReservado
        BigDecimal stockMinimoAlerta
        BigDecimal precioUnitario
        +getStockDisponible() BigDecimal
        +reservar(BigDecimal cantidad)
        +consumirReserva(BigDecimal cantidad)
        +liberarReserva(BigDecimal cantidad)
        +estaEnStockCritico() boolean
    }
    class Maquinaria {
        Long id
        String nombre
        TipoMaquinaria tipo
        String matriculaOSerie
        BigDecimal horasUsoActuales
        BigDecimal horasParaProximoService
        BigDecimal costoOperativoHora
        EstadoMaquinaria estado
        +sumarHoras(BigDecimal horas)
        +requiereService() boolean
    }
    class RegistroMantenimiento {
        Long id
        LocalDate fechaService
        TipoMantenimiento tipo
        BigDecimal costo
        BigDecimal proximoServiceHoras
    }
    class ReglaClimatica {
        Long id
        TipoLabor tipoLabor
        BigDecimal vientoMaxKmh
        BigDecimal vientoMinKmh
        BigDecimal tempMinC
        BigDecimal tempMaxC
        BigDecimal probLluviaMaxPct
        BigDecimal humedadMinPct
        +evaluar(DatosClima datos) ResultadoValidacion
    }
    class LaborAgricola {
        Long id
        TipoLabor tipoLabor
        LocalDateTime fechaPlanificada
        LocalDateTime fechaEjecucion
        EstadoLabor estado
        BigDecimal horasMaquinaConsumidas
        BigDecimal costoTotalCalculado
        +aprobarClima(DatosClima datos)
        +bloquearPorClima(String motivo)
        +iniciar()
        +completar(BigDecimal horas)
        +cancelar()
    }
    class LaborInsumo {
        Long id
        BigDecimal cantidadUtilizada
        BigDecimal costoSubtotal
        EstadoReserva estadoReserva
    }
    class RegistroCosecha {
        Long id
        LocalDate fechaCosecha
        BigDecimal toneladasTotales
        BigDecimal rindeTonPorHa
        BigDecimal precioVentaPorTon
        BigDecimal ingresoBrutoTotal
        BigDecimal margenNetoCalculado
        +calcularRoi() BigDecimal
    }
    class Cultivo {
        Long id
        String nombre
        String especie
    }
    class Proveedor {
        Long id
        String nombre
        String cuit
    }
    class CompraInsumo {
        Long id
        LocalDateTime fechaCompra
        BigDecimal totalCompra
    }
    class DetalleCompra {
        Long id
        BigDecimal cantidad
        BigDecimal precioUnitario
        BigDecimal subtotal
    }
    class MovimientoStock {
        Long id
        LocalDateTime fecha
        TipoMovimiento tipo
        BigDecimal cantidad
        String observacion
    }

    Lote "1" --> "*" LaborAgricola
    Lote "1" --> "*" RegistroCosecha
    LaborAgricola "*" --> "0..1" Maquinaria
    Maquinaria "1" --> "*" RegistroMantenimiento
    LaborAgricola "1" *-- "*" LaborInsumo
    LaborInsumo "*" --> "1" Insumo
    LaborAgricola ..> ReglaClimatica : valida con
    RegistroCosecha "*" --> "1" Cultivo
    RegistroCosecha "0..1" --> "0..1" LaborAgricola
    Proveedor "1" --> "*" CompraInsumo
    CompraInsumo "1" *-- "*" DetalleCompra
    DetalleCompra "*" --> "1" Insumo
    MovimientoStock "*" --> "1" Insumo
    MovimientoStock "*" --> "0..1" LaborAgricola
    MovimientoStock "*" --> "0..1" CompraInsumo
    LaborAgricola "*" --> "0..1" Usuario : responsable
    MovimientoStock "*" --> "1" Usuario : responsable
    CompraInsumo "*" --> "1" Usuario : responsable
```

## 8.6. Diagrama de Secuencia (Contingencia ante caída de la API Climática)

Detalla el flujo alternativo 3a del CU-04 y la estrategia de la sección 7.3 del doc 07.

```mermaid
sequenceDiagram
    actor Usr as Agrónomo / Operario
    participant UI as Frontend (React)
    participant SRV as LaborService
    participant CLI as ClimaClient (Resilience4j)
    participant CACHE as Caché de clima
    participant Ext as API Climática
    participant DB as Base de Datos

    Usr->>UI: Clic en "Iniciar Labor"
    UI->>SRV: POST /api/labores/{id}/iniciar
    SRV->>CLI: obtenerClima(lat, lon)
    CLI->>Ext: GET clima (timeout 3 s)
    Ext--xCLI: Error / timeout
    CLI->>Ext: Reintentos (máx. 2, backoff)
    Ext--xCLI: Error
    Note over CLI: Circuit breaker registra la falla

    CLI->>CACHE: Buscar lectura de las coordenadas
    alt Lectura en caché con menos de 30 min
        CACHE-->>CLI: Datos climáticos
        CLI-->>SRV: Datos (origen: caché)
        SRV->>DB: Valida contra regla_climatica y continúa el flujo normal
        SRV-->>UI: 200 OK (validado con datos en caché)
    else Sin caché vigente
        CACHE-->>CLI: Vacío
        CLI-->>SRV: ClimaNoDisponibleException
        SRV-->>UI: 503 Service Unavailable (labor sin cambios)
        UI-->>Usr: "Validación climática no disponible"
        opt Usuario AGRONOMO o ADMIN
            Usr->>UI: Autorizar manualmente + justificación
            UI->>SRV: POST /api/labores/{id}/autorizacion-manual
            SRV->>DB: UPDATE labor (APROBADA_CLIMA, observaciones_climaticas = justificación)
            SRV-->>UI: 200 OK (puede iniciar la labor)
        end
    end
```

## 8.7. Diagrama de Secuencia (Registrar Compra de Insumos)

Flujo del CU-02. Toda la operación se ejecuta en una única transacción: si falla una línea, no se persiste ninguna.

```mermaid
sequenceDiagram
    actor Adm as Administrador
    participant UI as Frontend (React)
    participant SRV as CompraService
    participant DB as Base de Datos

    Adm->>UI: Carga proveedor, insumos, cantidades y precios
    UI->>SRV: POST /api/compras (JWT con rol ADMIN)
    SRV->>SRV: Valida cantidades > 0 y calcula subtotales y total
    SRV->>DB: BEGIN TRANSACTION
    SRV->>DB: INSERT compra_insumo (total_compra, usuario_id)
    loop Por cada línea
        SRV->>DB: INSERT detalle_compra (subtotal)
        SRV->>DB: UPDATE insumo (stock_actual += cantidad, precio_unitario)
        SRV->>DB: INSERT movimiento_stock (INGRESO_COMPRA, compra_id)
    end
    SRV->>DB: COMMIT
    SRV-->>UI: 201 Created
    UI-->>Adm: Compra registrada y stock actualizado
```

## 8.8. Diagrama de Actividad (Programar Labor con Reserva de Stock)

Flujo del CU-03 y de la regla RN-07.

```mermaid
flowchart TD
    A([Inicio]) --> B[Agrónomo selecciona lote, tipo de labor, fecha, maquinaria e insumos]
    B --> C{¿Lote en estado válido?}
    C -- No --> X1[Rechazar: estado del lote no permite la labor] --> Z([Fin])
    C -- Sí --> D{¿Maquinaria OPERATIVO?}
    D -- No --> X2[Rechazar: maquinaria no disponible] --> Z
    D -- Sí --> E[Calcular stock disponible = stock_actual - stock_reservado]
    E --> F{¿Disponible >= requerido para todos los insumos?}
    F -- No --> X3[Rechazar indicando cantidad faltante] --> Z
    F -- Sí --> G[Crear labor en PLANIFICADA]
    G --> H[Crear labor_insumo en RESERVADO]
    H --> I[Incrementar insumo.stock_reservado]
    I --> J[Confirmar transacción]
    J --> Z
```

## 8.9. Diagrama de Despliegue

Distribución física de los componentes en el perfil `prod`.

```mermaid
flowchart LR
    subgraph Cliente ["Dispositivo del usuario"]
        BR[Navegador web / móvil]
    end

    subgraph Nube ["Proveedor Cloud"]
        subgraph FE ["Hosting estático"]
            SPA[SPA React - build estático]
        end
        subgraph BE ["Servicio de aplicación"]
            APP[Spring Boot JAR - perfil prod]
            ACT[Actuator: health y metrics]
        end
        subgraph DBS ["Base de datos gestionada"]
            PG[(PostgreSQL)]
        end
    end

    subgraph EXT ["Servicio de terceros"]
        WAPI[API Climática]
    end

    BR -- HTTPS --> SPA
    BR -- HTTPS / REST + JWT --> APP
    APP -- JDBC --> PG
    APP -- HTTPS --> WAPI
    APP --- ACT
```
