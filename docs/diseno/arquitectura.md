# Arquitectura

Parte del [DI3 — Diseño de la solución](DI3-diseno-solucion.md).

Convenciones de los diagramas:
- **Línea punteada** o etiqueta **«pendiente»**: componente de la Persona 1 o de la Persona 2 que todavía no existe. Se dibuja con el nombre de la interfaz que debe implementar.
- Fondo amarillo: clases de **demostración** (paquete `demo`), provisionales hasta que la Persona 2 las reemplace.
- Los nombres de paquetes, clases, exchanges y colas son los del código (`src/main/java/cr/ac/tec/convitec`).

## a) Componentes de una sede

Cada sede (LIB, ALA, SJO o LIM) es un proceso Java independiente con todos estos componentes. `app.Sede` los crea a partir de su `ConfigSede` y los conecta.

```mermaid
flowchart TB
    classDef pendiente stroke-dasharray: 5 5,fill:#f4f4f4,color:#555
    classDef demo fill:#fff4d6,stroke:#c9a227

    empleado(["Empleado de la sede"])

    subgraph presentacion["Presentación"]
        web["Interfaz web y API REST<br/>«pendiente: Persona 1»"]:::pendiente
        consola["app.ConsolaSede<br/>consola de prueba"]
    end

    subgraph arranque["app y config"]
        main["app.Main<br/>ArgumentosSede"]
        sede["app.Sede<br/>conDemostracion"]
        config["config.ConfigSede<br/>SedesConocidas"]
        props[("sedes/sede-COD.properties")]
    end

    subgraph servicios["Servicios de la sede"]
        srutas["rutas.ServicioRutas<br/>implementa IServicioRutas"]
        sentregas["entregas.ServicioEntregas"]
        sestad["estadisticas.ServicioEstadisticas<br/>implementa IServicioEstadisticas"]
        spedidos["Servicio de pedidos<br/>«pendiente: Persona 2»"]:::pendiente
    end

    subgraph dominio["Lógica"]
        grafo["grafo<br/>Grafo, Dijkstra, CargadorGrafo"]
        rutas["rutas<br/>PlanificadorEntregas, SalidasDiarias"]
        estad["estadisticas<br/>Ordenamiento, Comparador"]
        modelo["modelo<br/>Venta, DetalleVenta"]
        rutasjson[("rutas.json")]
    end

    subgraph mensajeria["mensajeria"]
        mrabbit["MensajeriaRabbit<br/>implementa IMensajeria"]
        proc["ProcesadorMensajes"]
        pend["RespuestasPendientes"]
    end

    subgraph contratos["Contratos de la Persona 2 (paquete servicios)"]
        iinv["IInventarioLocal"]
        icat["ICatalogoGlobal"]
        iges["IGestorPedidos"]
        irep["IRepositorioVentas"]
    end

    subgraph impl["Implementaciones"]
        invdemo["demo.InventarioDemo"]:::demo
        catdemo["demo.CatalogoGlobalDemo"]:::demo
        repmem["entregas.RepositorioVentasMemoria"]:::demo
        invreal["Inventario en archivos<br/>«pendiente»"]:::pendiente
        catreal["Catálogo global con AVL<br/>«pendiente»"]:::pendiente
        gesreal["Gestor de pedidos con cola<br/>«pendiente»"]:::pendiente
        repreal["Repositorio de ventas en archivos<br/>«pendiente»"]:::pendiente
        archivos[("datos/COD<br/>archivos de la sede<br/>«pendiente»")]:::pendiente
    end

    broker{{"Broker RabbitMQ"}}

    empleado -.-> web
    empleado --> consola
    main --> config
    config --> props
    main --> sede
    main --> consola
    consola --> sede
    web -.-> sede

    sede --> srutas
    sede --> sestad
    sede --> mrabbit
    sede -.->|"cuando exista IGestorPedidos"| sentregas
    web -.-> spedidos

    srutas --> rutas
    rutas --> grafo
    grafo --> rutasjson
    sestad --> estad
    sestad --> irep
    sentregas --> iges
    sentregas --> irep
    sentregas --> modelo
    estad --> modelo

    spedidos -.-> iinv
    spedidos -.-> mrabbit
    spedidos -.-> srutas

    mrabbit --> proc
    mrabbit --> pend
    proc --> iinv
    proc --> icat
    mrabbit <-->|"AMQP 5672"| broker

    iinv --> invdemo
    icat --> catdemo
    irep --> repmem
    iinv -.-> invreal
    icat -.-> catreal
    iges -.-> gesreal
    irep -.-> repreal
    invreal -.-> archivos
    catreal -.-> archivos
    gesreal -.-> archivos
    repreal -.-> archivos
```

Notas:
- `ServicioEntregas` ya está implementado y probado, pero `Sede` todavía no lo crea porque necesita un `IGestorPedidos`, que es de la Persona 2.
- Hoy la sede arranca con las clases de `demo`. La Persona 2 cambia las 3 líneas marcadas en `Sede.conDemostracion` por sus clases reales.

## b) Despliegue en 2 computadoras

### Computadoras y procesos

```mermaid
flowchart LR
    subgraph mac["Computadora 1: Mac (broker)"]
        direction TB
        rabbit{{"RabbitMQ 4<br/>puerto 5672"}}
        lib["Sede LIB<br/>java -jar convitec-sede.jar LIB<br/>HTTP 8081"]
        ala["Sede ALA<br/>java -jar convitec-sede.jar ALA<br/>HTTP 8082"]
        dlib[("datos/LIB")]
        dala[("datos/ALA")]
        lib --- dlib
        ala --- dala
    end

    subgraph win["Computadora 2: Windows"]
        direction TB
        sjo["Sede SJO<br/>--config sede-SJO.properties<br/>HTTP 8083"]
        lim["Sede LIM<br/>--config sede-LIM.properties<br/>HTTP 8084"]
        dsjo[("datos/SJO")]
        dlim[("datos/LIM")]
        sjo --- dsjo
        lim --- dlim
    end

    lib <-->|"AMQP localhost:5672<br/>usuario guest"| rabbit
    ala <-->|"AMQP localhost:5672<br/>usuario guest"| rabbit
    sjo <-->|"AMQP IP-de-la-Mac:5672<br/>usuario convitec"| rabbit
    lim <-->|"AMQP IP-de-la-Mac:5672<br/>usuario convitec"| rabbit
```

- Cada sede usa solo sus archivos (`config.getCarpetaDatos()`), y no lee los de las otras.
- Las sedes de Windows usan un `.properties` externo (`--config`) con `rabbit.host` = IP de la Mac. La clave se toma de `CONVITEC_RABBIT_CLAVE`.
- Requisitos de red en la Mac: comentar `NODE_IP_ADDRESS=127.0.0.1` en `rabbitmq-env.conf`, crear un usuario distinto de `guest` y abrir el puerto 5672. El paso a paso está en el [README](../../README.md).

### Topología de RabbitMQ

Definida en `mensajeria.TopologiaRabbit` y declarada por `MensajeriaRabbit.iniciar()`.

```mermaid
flowchart LR
    classDef exchange fill:#e3f0ff,stroke:#3b78c2
    classDef cola fill:#eef8ee,stroke:#3c8c3c

    pub["Sede que publica<br/>MensajeriaRabbit"]

    directo{"convitec.directo<br/>tipo direct"}:::exchange
    stock{"convitec.stock<br/>tipo fanout"}:::exchange

    qsLIB[["convitec.sede.LIB"]]:::cola
    qsALA[["convitec.sede.ALA"]]:::cola
    qsSJO[["convitec.sede.SJO"]]:::cola
    qsLIM[["convitec.sede.LIM"]]:::cola

    qkLIB[["convitec.stock.LIB"]]:::cola
    qkALA[["convitec.stock.ALA"]]:::cola
    qkSJO[["convitec.stock.SJO"]]:::cola
    qkLIM[["convitec.stock.LIM"]]:::cola

    resp[["amq.gen-... exclusiva<br/>respuestas de ALA"]]:::cola

    pub -->|"SOLICITAR_RESERVA<br/>CONFIRMAR_RESERVA<br/>LIBERAR_RESERVA"| directo
    pub -->|"ACTUALIZACION_STOCK"| stock
    pub -->|"RESPUESTA_RESERVA<br/>exchange por defecto, routing key = replyTo"| resp

    directo -->|"routing key LIB"| qsLIB
    directo -->|"routing key ALA"| qsALA
    directo -->|"routing key SJO"| qsSJO
    directo -->|"routing key LIM"| qsLIM

    stock --> qkLIB
    stock --> qkALA
    stock --> qkSJO
    stock --> qkLIM

    qsLIB --> cLIB["Sede LIB"]
    qkLIB --> cLIB
    qsALA --> cALA["Sede ALA"]
    qkALA --> cALA
    resp --> cALA
    qsSJO --> cSJO["Sede SJO"]
    qkSJO --> cSJO
    qsLIM --> cLIM["Sede LIM"]
    qkLIM --> cLIM
```

- Las colas `convitec.sede.COD` y `convitec.stock.COD` son **durables** (no exclusivas, sin autoDelete). Si una sede está apagada, sus mensajes la esperan.
- La cola de respuestas la crea el servidor (`queueDeclare()`). Es **exclusiva** y se borra sola cuando la sede se desconecta. Cada sede tiene la suya; en el diagrama solo se muestra la de ALA.
- La sede que publica en `convitec.stock` también recibe su propia copia en `convitec.stock.COD`, y `ProcesadorMensajes` la ignora.

## c) Paquetes y dependencias

Dependencias reales, obtenidas de los `import` de `src/main/java` (A → B significa "A usa clases de B"):

```mermaid
flowchart TB
    classDef ciclo stroke:#c0392b,stroke-width:2px

    app["app"]
    demo["demo"]
    entregas["entregas"]
    estadisticas["estadisticas"]:::ciclo
    rutas["rutas"]:::ciclo
    mensajeria["mensajeria"]:::ciclo
    servicios["servicios"]:::ciclo
    grafo["grafo"]
    modelo["modelo"]
    config["config"]

    app --> config
    app --> demo
    app --> entregas
    app --> estadisticas
    app --> grafo
    app --> mensajeria
    app --> rutas
    app --> servicios

    demo --> mensajeria
    demo --> servicios
    entregas --> modelo
    entregas --> rutas
    entregas --> servicios
    estadisticas --> modelo
    mensajeria --> config
    rutas --> grafo
    servicios --> grafo
    servicios --> modelo

    servicios <-->|"ciclo"| rutas
    servicios <-->|"ciclo"| estadisticas
    servicios <-->|"ciclo"| mensajeria
```

Qué muestra:
- `grafo`, `modelo` y `config` no dependen de nadie: son la base.
- `app` es el único paquete que conoce a todos, porque es el que arma la sede.
- Los módulos de cada integrante se conectan por las interfaces de `servicios`. Por ejemplo, `entregas` solo conoce `IGestorPedidos` e `IRepositorioVentas`, y `mensajeria` solo conoce `IInventarioLocal` e `ICatalogoGlobal`.

**Ciclos que existen hoy (en rojo).** Hay 3 ciclos de dos paquetes: `servicios` ↔ `rutas`, `servicios` ↔ `estadisticas` y `servicios` ↔ `mensajeria`. Aparecen porque las interfaces de `servicios` devuelven clases de resultado que viven en los paquetes que las implementan:

| Interfaz en `servicios` | Usa clases de |
|---|---|
| `IServicioRutas` | `grafo.ResultadoRuta`, `rutas.SolicitudEntrega`, `rutas.ParadaEntrega`, `rutas.RecorridoEntrega` |
| `IServicioEstadisticas` | `estadisticas.ProductoEstrella`, `estadisticas.ResumenProducto` |
| `IMensajeria`, `IInventarioLocal` | `mensajeria.RespuestaReserva` |

Estos ciclos no impiden compilar ni probar el proyecto, pero hacen que los paquetes no se puedan separar. **Mejora propuesta** (no aplicada): mover esas clases de resultado, que son inmutables y no tienen lógica, a `modelo`. Así `servicios` dependería solo de `modelo` y el grafo de paquetes quedaría sin ciclos.
