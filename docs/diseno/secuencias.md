# Diagramas de secuencia

Parte del [DI3 — Diseño de la solución](DI3-diseno-solucion.md).

Convenciones:
- **«pendiente»**: participante de la Persona 1 o de la Persona 2 que todavía no existe. Se nombra por la interfaz que implementará (`IInventarioLocal`, `IGestorPedidos`...). El "servicio de pedidos" de la Persona 2 todavía no tiene nombre de clase.
- Los métodos, tipos de mensaje, exchanges y colas son los reales del código.
- Los productos y cantidades son los de las clases de demostración (`demo.InventarioDemo`). Por ejemplo, B01 "Bebida" solo existe en LIM.
- Ruta de los mensajes: `MensajeriaRabbit` publica en `convitec.directo` con routing key = código de la sede destino, y el mensaje llega a la cola `convitec.sede.COD`. Las respuestas van a la cola exclusiva indicada en `replyTo`, y las actualizaciones de stock a `convitec.stock` (fanout).

## a) Pedido exitoso con productos de 2 sedes

El empleado de **ALA** registra el pedido **P7**: 1 C01 (Café, hay en ALA) y 2 B01 (Bebida, solo hay en LIM), con destino **Heredia**.

```mermaid
sequenceDiagram
    autonumber
    actor Emp as Empleado ALA
    participant UI as Interfaz web «pendiente»
    participant SP as Servicio de pedidos ALA «pendiente»
    participant InvA as IInventarioLocal ALA «pendiente»
    participant MA as MensajeriaRabbit ALA
    participant RP as RespuestasPendientes ALA
    participant MQ as RabbitMQ
    participant ML as MensajeriaRabbit LIM
    participant PL as ProcesadorMensajes LIM
    participant InvL as IInventarioLocal LIM «pendiente»
    participant SR as ServicioRutas ALA
    participant Cat as ICatalogoGlobal de LIB, ALA y SJO

    Emp->>UI: Registrar P7: 1 C01 y 2 B01, destino Heredia
    UI->>SP: registrar pedido P7

    Note over SP,InvA: 1. Reserva local
    SP->>InvA: reservar(P7, C01, 1)
    InvA-->>SP: RespuestaReserva.ok

    Note over SP,InvL: 2. Reserva en otra sede
    SP->>MA: solicitarReserva(LIM, P7, B01, 2)
    MA->>RP: registrar(correlationId)
    MA->>MQ: SOLICITAR_RESERVA a convitec.directo, key LIM, replyTo, expiration 5000
    MA->>RP: esperar(correlationId, 5000)
    Note right of RP: el hilo del pedido queda en wait()
    MQ->>ML: entrega desde convitec.sede.LIM
    ML->>PL: procesar(mensaje)
    PL->>InvL: reservar(P7, B01, 2)
    InvL-->>PL: RespuestaReserva.ok
    PL-->>ML: RESPUESTA_RESERVA exito=true, mismo correlationId
    ML->>MQ: publica en la cola replyTo de ALA y hace basicAck
    MQ->>MA: RESPUESTA_RESERVA
    MA->>RP: completar(correlationId, respuesta)
    Note right of RP: notifyAll despierta a esperar()
    RP-->>MA: respuesta
    MA-->>SP: RespuestaReserva ok, sede LIM

    Note over SP,SR: 3. Validar la ruta al destino
    SP->>SR: existeRuta(Alajuela, Heredia)
    SR-->>SP: true (Dijkstra)

    Note over SP,Cat: 4. Confirmar todo
    SP->>InvA: confirmar(P7, C01)
    InvA-->>SP: stock nuevo de C01
    SP->>MA: publicarActualizacionStock(C01, stock nuevo)
    MA->>MQ: ACTUALIZACION_STOCK a convitec.stock
    SP->>MA: confirmarReserva(LIM, P7, B01)
    MA->>MQ: CONFIRMAR_RESERVA a convitec.directo, key LIM
    MQ->>ML: entrega desde convitec.sede.LIM
    ML->>PL: procesar(mensaje)
    PL->>InvL: confirmar(P7, B01)
    InvL-->>PL: 3
    PL-->>ML: ACTUALIZACION_STOCK de LIM, B01 = 3
    ML->>MQ: publica en convitec.stock (fanout)
    MQ->>Cat: copia en convitec.stock.LIB, .ALA y .SJO
    Note over Cat: ProcesadorMensajes de cada sede llama actualizarStock(LIM, B01, 3)
    Note over MQ,ML: LIM también recibe su copia en convitec.stock.LIM y la ignora
    SP-->>UI: pedido P7 aceptado
    UI-->>Emp: Pedido aceptado
```

## b) Pedido rechazado por falta de stock

El empleado de **ALA** registra **P8**: 1 C01 (ALA), 2 B01 (LIM) y 10 S01 (Sándwich, solo hay en SJO, con 6 unidades). Cuando SJO rechaza, ya hay unidades reservadas en ALA y en LIM, y hay que **liberarlas todas**.

```mermaid
sequenceDiagram
    autonumber
    actor Emp as Empleado ALA
    participant SP as Servicio de pedidos ALA «pendiente»
    participant InvA as IInventarioLocal ALA «pendiente»
    participant MA as MensajeriaRabbit ALA
    participant MQ as RabbitMQ
    participant LIM as Sede LIM
    participant SJO as Sede SJO

    Emp->>SP: registrar P8: 1 C01, 2 B01, 10 S01
    SP->>InvA: reservar(P8, C01, 1)
    InvA-->>SP: ok

    SP->>MA: solicitarReserva(LIM, P8, B01, 2)
    MA->>MQ: SOLICITAR_RESERVA, key LIM
    MQ->>LIM: convitec.sede.LIM
    Note over LIM: ProcesadorMensajes → IInventarioLocal.reservar
    LIM-->>MQ: RESPUESTA_RESERVA exito=true
    MQ-->>MA: respuesta
    MA-->>SP: ok, sede LIM

    SP->>MA: solicitarReserva(SJO, P8, S01, 10)
    MA->>MQ: SOLICITAR_RESERVA, key SJO
    MQ->>SJO: convitec.sede.SJO
    Note over SJO: reservar devuelve rechazo: stock insuficiente de S01 (hay 6, se piden 10)
    SJO-->>MQ: RESPUESTA_RESERVA exito=false, motivo
    MQ-->>MA: respuesta
    MA-->>SP: rechazo: San José: stock insuficiente de S01 (hay 6, se piden 10)

    Note over SP,LIM: Algo falló: se libera TODO lo reservado y no se confirma nada
    SP->>InvA: liberar(P8, C01)
    SP->>MA: liberarReserva(LIM, P8, B01)
    MA->>MQ: LIBERAR_RESERVA, key LIM
    MQ->>LIM: convitec.sede.LIM
    Note over LIM: IInventarioLocal.liberar devuelve las 2 unidades
    SP-->>Emp: P8 rechazado: San José: stock insuficiente de S01 (hay 6, se piden 10)
```

No se publica ninguna `ACTUALIZACION_STOCK`, porque una reserva liberada no cambia el stock confirmado.

## c) Pedido rechazado por ruta

El empleado de **LIB** registra **P9**: 1 C01 (LIB) y 1 B01 (LIM), con destino **Cartago**. Las reservas salen bien (la mensajería no depende de las carreteras), pero Liberia y Cañas forman un componente aislado del grafo, así que no hay ruta a Cartago.

```mermaid
sequenceDiagram
    autonumber
    actor Emp as Empleado LIB
    participant SP as Servicio de pedidos LIB «pendiente»
    participant InvB as IInventarioLocal LIB «pendiente»
    participant MB as MensajeriaRabbit LIB
    participant MQ as RabbitMQ
    participant LIM as Sede LIM
    participant SR as ServicioRutas LIB
    participant DJ as Dijkstra

    Emp->>SP: registrar P9: 1 C01, 1 B01, destino Cartago
    SP->>InvB: reservar(P9, C01, 1)
    InvB-->>SP: ok
    SP->>MB: solicitarReserva(LIM, P9, B01, 1)
    MB->>MQ: SOLICITAR_RESERVA, key LIM
    MQ->>LIM: convitec.sede.LIM
    LIM-->>MQ: RESPUESTA_RESERVA exito=true
    MQ-->>MB: respuesta
    MB-->>SP: ok, sede LIM

    SP->>SR: existeRuta(Liberia, Cartago)
    SR->>DJ: existeRuta(grafo, Liberia, Cartago)
    Note over DJ: Desde Liberia solo se alcanza Cañas: dist de Cartago = SIN_CONEXION
    DJ-->>SR: false
    SR-->>SP: false
    SP->>SR: caminoMasCorto(Liberia, Cartago)
    SR-->>SP: ResultadoRuta esAlcanzable=false: Liberia -> Cartago: no hay ruta

    Note over SP,LIM: Sin ruta: se libera todo
    SP->>InvB: liberar(P9, C01)
    SP->>MB: liberarReserva(LIM, P9, B01)
    MB->>MQ: LIBERAR_RESERVA, key LIM
    MQ->>LIM: convitec.sede.LIM
    SP-->>Emp: P9 rechazado: Liberia -> Cartago: no hay ruta
```

Un destino inalcanzable **no es un error**: `Dijkstra.caminoMasCorto` devuelve un `ResultadoRuta` con `esAlcanzable() = false` en lugar de lanzar una excepción. Solo se lanza `GrafoException` si la ubicación no existe.

## d) Una sede que no responde (timeout)

El empleado de **ALA** pide 1 S01 a **SJO**, pero la sede SJO está apagada o muy lenta. `reserva.timeoutMs` = 5000.

```mermaid
sequenceDiagram
    autonumber
    participant SP as Servicio de pedidos ALA «pendiente»
    participant MA as MensajeriaRabbit ALA
    participant RP as RespuestasPendientes ALA
    participant MQ as RabbitMQ
    participant QS as Cola convitec.sede.SJO
    participant SJO as Sede SJO

    SP->>MA: solicitarReserva(SJO, P10, S01, 1)
    MA->>RP: registrar(correlationId)
    MA->>MQ: SOLICITAR_RESERVA, key SJO, expiration 5000
    MQ->>QS: queda en la cola durable
    MA->>RP: esperar(correlationId, 5000)
    Note over RP: nadie llama completar(): wait() vence a los 5000 ms
    RP-->>MA: null y elimina la entrada
    MA-->>SP: rechazo: San José no respondió a tiempo

    Note over SP,QS: Por si SJO alcanzó a reservar, se libera igual
    SP->>MA: liberarReserva(SJO, P10, S01)
    MA->>MQ: LIBERAR_RESERVA, key SJO, sin expiration
    MQ->>QS: queda DETRÁS de la SOLICITAR (FIFO)

    alt SJO estaba apagada y arranca después
        Note over QS: la SOLICITAR venció (expiration): RabbitMQ la descarta
        QS->>SJO: LIBERAR_RESERVA
        Note over SJO: liberar sobre una reserva que no existe: no hace nada (idempotente)
    else SJO solo estaba lenta
        QS->>SJO: SOLICITAR_RESERVA
        Note over SJO: reserva 1 S01 y responde
        SJO-->>MQ: RESPUESTA_RESERVA
        MQ-->>MA: respuesta tardía
        MA->>RP: completar(correlationId, respuesta)
        RP-->>MA: false: ya nadie la espera, se ignora
        QS->>SJO: LIBERAR_RESERVA
        Note over SJO: liberar devuelve la unidad
    end
    Note over SJO: En ambos casos no queda ninguna unidad reservada para siempre
```

Por qué no hay inconsistencias:
1. La `SOLICITAR_RESERVA` y la `LIBERAR_RESERVA` van a la **misma cola** (`convitec.sede.SJO`), por el mismo exchange y desde el mismo canal de publicación. RabbitMQ mantiene el orden FIFO, así que el LIBERAR siempre llega **después** de la solicitud.
2. SJO consume su cola con **un solo consumidor** y `basicQos(1)`, y hace `basicAck` al terminar cada mensaje. Así procesa un mensaje a la vez, en orden.
3. La solicitud lleva **expiration = timeout**: si SJO no la procesó a tiempo, RabbitMQ la descarta y una solicitud vieja no aparta unidades horas después.
4. `IInventarioLocal.liberar` es **idempotente**: liberar una reserva que no existe no hace nada.
5. Una respuesta tardía no confunde a nadie: `RespuestasPendientes.completar` devuelve false y la respuesta se ignora.

Supuesto: hay **una sola instancia** por sede. Con dos consumidores sobre la misma cola, el orden de procesamiento no estaría garantizado.

## e) Salida de entregas paso a paso

Sede **San José**, salida de las 11:00, con los pendientes P1 → Limón, P2 → Heredia, P3 → Cartago y P4 → Heredia. Cada vez que la interfaz pide la siguiente entrega (botón "Calcular siguiente punto de entrega"), se registra esa parada como venta.

```mermaid
sequenceDiagram
    autonumber
    participant UI as Interfaz web «pendiente»
    participant SR as ServicioRutas SJO
    participant PE as PlanificadorEntregas
    participant DJ as Dijkstra
    participant SE as ServicioEntregas
    participant GP as IGestorPedidos «pendiente»
    participant RV as IRepositorioVentas

    UI->>SR: iniciarRecorrido([P1 Limón, P2 Heredia, P3 Cartago, P4 Heredia], 11:00)
    SR->>PE: iniciarRecorrido(pendientes, 11:00)
    PE->>DJ: calcular(grafo, San José)
    DJ-->>PE: ResultadoDijkstra: Heredia 20, Cartago 45, Limón 180
    Note over PE: todos los destinos son alcanzables

    Note over UI,RV: Parada 1
    UI->>SR: calcularSiguienteEntrega()
    SR->>PE: calcularSiguienteEntrega()
    Note over PE: menor tiempo: Heredia 20. Se entregan juntos P2 y P4
    PE->>DJ: calcular(grafo, Heredia)
    DJ-->>PE: Cartago 65, Limón 200
    PE-->>SR: ParadaEntrega 1: San José -> Heredia, tramo 20, acumulado 20, [P2, P4]
    SR-->>UI: parada 1
    UI->>SE: registrarParada(parada 1, 11:00, fechaHora)
    loop por cada id: P2 y P4 (primero se validan TODOS)
        SE->>RV: buscarPorIdPedido(id)
        RV-->>SE: null (no hay venta duplicada)
        SE->>GP: obtenerDetalle(id)
        GP-->>SE: DetalleVenta[]
        Note over SE: new Venta(V-id, id, San José, Heredia, fechaHora, 11:00, detalles, 20)
    end
    loop por cada venta (solo si todas son válidas)
        SE->>RV: guardar(venta)
        SE->>GP: marcarEntregado(id, fechaHora)
    end
    SE-->>UI: Venta[] V-P2 y V-P4, tiempoTraslado 20

    Note over UI,RV: Parada 2
    UI->>SR: calcularSiguienteEntrega()
    SR->>PE: calcularSiguienteEntrega()
    Note over PE: menor tiempo desde Heredia: Cartago 65
    PE->>DJ: calcular(grafo, Cartago)
    DJ-->>PE: Limón 135
    PE-->>SR: ParadaEntrega 2: Heredia -> San José -> Cartago, tramo 65, acumulado 85, [P3]
    SR-->>UI: parada 2
    UI->>SE: registrarParada(parada 2, 11:00, fechaHora)
    SE-->>UI: Venta[] V-P3, tiempoTraslado 85

    Note over UI,RV: Parada 3
    UI->>SR: calcularSiguienteEntrega()
    SR->>PE: calcularSiguienteEntrega()
    Note over PE: único pendiente: Limón 135. No quedan pendientes, no se corre Dijkstra
    PE-->>SR: ParadaEntrega 3: Cartago -> Paraíso -> Turrialba -> Limón, tramo 135, acumulado 220, [P1]
    SR-->>UI: parada 3
    UI->>SE: registrarParada(parada 3, 11:00, fechaHora)
    SE-->>UI: Venta[] V-P1, tiempoTraslado 220

    UI->>SR: hayEntregasPendientes()
    SR-->>UI: false
    UI->>SR: obtenerRecorrido()
    SR-->>UI: RecorridoEntrega: 3 paradas, tiempo total 220, 4 pedidos
```

Detalles:
- Dijkstra se ejecuta **una vez por parada**: una vez desde la sede al iniciar y luego desde cada nueva ubicación mientras queden pendientes. Para 3 paradas se ejecuta 3 veces, no una vez por cada pedido.
- El vehículo **no regresa** a la sede: el tiempo total (220) termina en la última entrega.
- El `tiempoTraslado` de cada venta es el `getTiempoAcumulado()` de su parada: P2 y P4 = 20, P3 = 85, P1 = 220.
- El modo completo (`planificarRecorridoCompleto` y `ServicioEntregas.registrarRecorrido`) hace lo mismo en un ciclo, reutilizando estos métodos.
