# DI3 — Diseño de la solución

<!-- TODO DI1: actualizar IDs. Los requerimientos se citan con la numeración de los requerimientos no funcionales (RNF) del enunciado; cuando exista el DI1, cambiarlos por sus IDs. Los que dicen "número por confirmar" no tienen todavía un RNF identificado. -->

ConviTEC simula una cadena de 4 tiendas de conveniencia: Liberia (LIB), Alajuela (ALA), San José (SJO) y Limón (LIM). Cada sede es una **aplicación Java independiente**, con sus propios archivos, y se comunica con las demás **solo por mensajes RabbitMQ**. Las 4 sedes se ejecutan en al menos 2 computadoras conectadas al mismo broker.

Este documento explica el diseño y las decisiones que lo justifican. Los diagramas están en archivos aparte:

| Documento | Contenido |
|---|---|
| [arquitectura.md](arquitectura.md) | Componentes de una sede, despliegue en 2 computadoras, topología de RabbitMQ y dependencias entre paquetes |
| [secuencias.md](secuencias.md) | Pedido exitoso entre 2 sedes, rechazo por stock, rechazo por ruta, timeout y salida de entregas paso a paso |
| [clases-persona3.md](clases-persona3.md) | Diagramas de clases de `grafo`, `rutas`, `modelo` (ventas), `entregas`, `estadisticas`, `mensajeria`, `config` y `app` |

**Estado.** Están implementados y probados (166 pruebas JUnit) el grafo, Dijkstra, el planificador de entregas, las ventas, las estadísticas, la mensajería, la configuración y el arranque. Faltan la interfaz web y la API REST (Persona 1), y el inventario, el catálogo global, los pedidos y la persistencia (Persona 2). Esas partes aparecen en los diagramas como **«pendiente»**, con el nombre de la interfaz que deben implementar.

## Organización

El código está en `cr.ac.tec.convitec`, separado por responsabilidad:

| Paquete | Responsabilidad |
|---|---|
| `grafo` | Mapa de rutas (matriz de adyacencia) y Dijkstra |
| `rutas` | Planificador de entregas y salidas diarias |
| `modelo` | Venta y DetalleVenta (Pedido y Producto: Persona 2) |
| `entregas` | Convierte las entregas en ventas |
| `estadisticas` | Historial, producto estrella, peores pedidos y MergeSort propio |
| `mensajeria` | Formato de mensajes y transporte RabbitMQ |
| `config` | Configuración de cada sede |
| `servicios` | Interfaces (contratos) entre los módulos de las integrantes |
| `demo` | Inventario y catálogo provisionales, para probar sin el código de la Persona 2 |
| `app` | Arranque (`Main`, `Sede`) y consola de prueba |

## Decisiones de diseño

### 1. Matriz de adyacencia para el grafo
**Requerimiento:** RNF 8, grafo no dirigido y ponderado con matriz de adyacencia. <!-- TODO DI1: actualizar IDs -->

`Grafo` guarda los tiempos en `int[][] matriz`, los nombres en `String[] nombres` y el id de cada ruta en `String[][] idsRuta`. Como el grafo es no dirigido, cada ruta se escribe en `[i][j]` y en `[j][i]`. La ausencia de ruta se representa con `SIN_CONEXION = Integer.MAX_VALUE`, y la diagonal vale 0. Con 13 ubicaciones, la matriz ocupa 169 celdas y `getPeso(i, j)` responde en O(1). Los arreglos crecen duplicando su tamaño si se agregan ubicaciones. Para que "san jose" encuentre a "San José", las búsquedas ignoran tildes, mayúsculas y espacios extra. `CargadorGrafo` lee `rutas.json` con Gson, directo a arreglos, y rechaza ids repetidos, rutas duplicadas, tiempos menores o iguales a 0 y rutas de una ubicación a sí misma.

### 2. Dijkstra O(V²) con arreglos
**Requerimiento:** RNF 10 (camino más corto con Dijkstra propio); restricción de no usar colecciones (número por confirmar). <!-- TODO DI1: actualizar IDs -->

`Dijkstra.calcular` usa tres arreglos (`dist[]`, `previo[]` y `visitado[]`), y en cada paso busca de forma lineal el vértice no visitado más cercano. Eso cuesta O(V²): con V = 13 son unas 169 comparaciones por ejecución, una cantidad despreciable. Una cola de prioridad daría O((V+E) log V), pero no se puede usar `PriorityQueue` y un heap propio agregaría código y riesgo sin ninguna ganancia práctica en un grafo de este tamaño. El algoritmo termina antes si el vértice más cercano es inalcanzable, y **nunca suma sobre `SIN_CONEXION`**, para evitar el desbordamiento. El resultado, `ResultadoDijkstra`, guarda las distancias y los previos hacia **todos** los vértices, y reconstruye cualquier camino recorriendo `previos[]` hacia atrás.

### 3. Destino más cercano y Dijkstra de nuevo tras cada entrega
**Requerimiento:** RNF 10, Dijkstra se ejecuta de nuevo tras cada entrega desde la ubicación actual. <!-- TODO DI1: actualizar IDs -->

`PlanificadorEntregas` aplica un algoritmo voraz:
1. Ejecuta Dijkstra desde la ubicación actual.
2. Elige el pendiente de menor tiempo.
3. Entrega y mueve el vehículo a ese destino.
4. Vuelve a ejecutar Dijkstra desde ahí.

Dijkstra se ejecuta **una vez por parada**, no una vez por cada pedido, porque `ResultadoDijkstra` ya trae las distancias a todos los destinos. Si hay un empate, gana el pedido que aparece primero en el arreglo de pendientes, para que el resultado sea predecible. El vehículo no regresa a la sede. Los destinos inalcanzables se detectan al iniciar, con el primer Dijkstra: como el grafo es no dirigido, lo que no se alcanza desde la sede tampoco se alcanza desde ningún punto del recorrido. Esos pedidos quedan en `idsNoAlcanzables`, cada uno con su explicación (por ejemplo, "No hay ruta de Liberia a Cartago"), y no se lanza ninguna excepción. El algoritmo no garantiza el recorrido global más corto, y el enunciado lo acepta. Se ofrece paso a paso (`calcularSiguienteEntrega`) y completo (`planificarRecorridoCompleto`); el modo completo reutiliza el paso a paso.

### 4. Entregas agrupadas por destino
**Requerimiento:** procedimiento de entregas del enunciado (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Si varios pedidos van al mismo destino, se entregan en la **misma** `ParadaEntrega` (`idsPedidos` contiene todos). Así no aparecen paradas de tramo 0 repetidas, y el tiempo de traslado de esos pedidos es el mismo.

### 5. El tiempo acumulado como tiempo de traslado
**Requerimiento:** peores pedidos, con rendimiento = monto ÷ tiempo de traslado (número por confirmar). <!-- TODO DI1: actualizar IDs -->

El tiempo de traslado de un pedido es el `tiempoAcumulado` de su parada: los minutos desde que el vehículo salió de la sede hasta esa entrega, no solo el último tramo. Así, un pedido que espera al final del recorrido refleja todo lo que tardó. Un pedido entregado en la propia sede tiene tiempo 0, y para el rendimiento se cuenta como 1 minuto, para no dividir entre cero.

### 6. La venta se crea al entregar
**Requerimiento:** registro de ventas con productos, cantidades, precio, monto, fecha y sede (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Un pedido se convierte en `Venta` cuando se **entrega**, no cuando se registra. `ServicioEntregas.registrarParada` pide el detalle a `IGestorPedidos` y crea una `Venta` con `idVenta = "V-" + idPedido`, `tiempoTraslado` = tiempo acumulado de la parada y `montoTotal` = suma de los subtotales. Después guarda la venta en `IRepositorioVentas` y marca el pedido como entregado. Cada parada es **todo o nada**: primero valida y crea todas sus ventas, y guarda solo si todas están bien. Las ventas no se duplican (`buscarPorIdPedido`). `Venta` y `DetalleVenta` son inmutables y validan sus datos.

### 7. MergeSort propio y estable
**Requerimiento:** algoritmos de ordenamiento propios, sin ordenamientos de librería (número por confirmar). <!-- TODO DI1: actualizar IDs -->

`Ordenamiento.ordenar` es un MergeSort genérico que recibe un `Comparador<T>` propio (no `java.util.Comparator`) y devuelve un arreglo nuevo, sin modificar el original. Se eligió MergeSort por dos razones:
- Es **O(n log n) en el peor caso**. QuickSort puede degradarse a O(n²) con datos ya ordenados, y el historial de ventas casi siempre llega ordenado por fecha.
- Es **estable**: dos elementos iguales conservan su orden. Eso hace predecibles los desempates.

`ServicioEstadisticas` lo usa con tres comparadores:
- **Historial:** fecha descendente, y si empatan, `idVenta`.
- **Ranking y producto estrella:** monto descendente, luego más unidades, luego código.
- **Peores pedidos:** rendimiento ascendente, luego más tiempo de traslado, luego `idPedido`.

### 8. Topología de RabbitMQ
**Requerimiento:** RNF 12–14, sedes independientes que se comunican por mensajes. <!-- TODO DI1: actualizar IDs -->

La topología está definida en `TopologiaRabbit`:
- **Exchange directo `convitec.directo`**: cada sede tiene la cola `convitec.sede.COD`, enlazada con la routing key = su código. Los mensajes dirigidos a una sede (`SOLICITAR_RESERVA`, `CONFIRMAR_RESERVA` y `LIBERAR_RESERVA`) llegan solo a ella.
- **Exchange fanout `convitec.stock`**: cada sede tiene la cola `convitec.stock.COD`. Una `ACTUALIZACION_STOCK` llega a **todas** sin que el emisor tenga que conocerlas.
- **Colas de sede durables**, no exclusivas y sin autoDelete. RabbitMQ 4 no permite colas no durables compartidas, y además así una sede apagada no pierde los mensajes que le enviaron.
- **Cola de respuestas exclusiva**, con nombre generado por el servidor. Va en `replyTo`, por lo que cada sede recibe solo sus respuestas, y se borra sola al desconectarse. Si la conexión se recupera sola, `MensajeriaRabbit` actualiza el nombre de esta cola.

Ninguna sede lee los archivos de otra: todo lo que una sede necesita de otra viaja como `Mensaje` en JSON.

### 9. Protocolo reservar → confirmar o liberar
**Requerimiento:** RNF 12–14 y la consistencia del inventario entre sedes (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Un pedido con productos de otras sedes sigue un protocolo en dos fases:
1. **Reservar** en cada sede (`solicitarReserva`). La sede aparta las unidades, y ningún otro pedido las puede tomar.
2. Si **todas** las reservas salieron bien y `existeRuta` da true, se **confirman** todas (`confirmarReserva`). La sede descuenta definitivamente y difunde su stock nuevo (`ACTUALIZACION_STOCK`) para que cada sede actualice su `ICatalogoGlobal`.
3. Si **algo** falla (falta de stock, sede sin respuesta o destino inalcanzable), se **liberan** todas las reservas hechas (`liberarReserva`) y se rechaza el pedido con el motivo.

`IInventarioLocal.confirmar` y `liberar` deben ser **idempotentes**, para que un mensaje repetido o tardío no descuente ni devuelva dos veces. La orquestación del pedido es de la Persona 2 (servicio de pedidos), y la mensajería ofrece las operaciones.

### 10. Timeouts y expiration
**Requerimiento:** RNF 12–14 y el manejo de casos especiales: sede caída o sin respuesta (número por confirmar). <!-- TODO DI1: actualizar IDs -->

- `solicitarReserva` **espera** la respuesta hasta `reserva.timeoutMs` (5000 ms). La espera la hace `RespuestasPendientes` con `synchronized`, `wait` y `notifyAll`, sin `CompletableFuture` ni colecciones concurrentes.
- Si el tiempo se vence, devuelve `exito = false` con "Sede no respondió a tiempo", y nunca lanza excepciones por problemas de red.
- La solicitud lleva **`expiration` = timeout**, para que una solicitud vieja no aparte unidades tarde.
- Un `LIBERAR_RESERVA` enviado después de un timeout llega a la misma cola, detrás de la solicitud (FIFO). Por eso se procesa **después**, o la solicitud ya venció.
- Las respuestas tardías se ignoran (`completar` devuelve false).

El detalle está en el [diagrama de secuencia d](secuencias.md#d-una-sede-que-no-responde-timeout).

### 11. Mensajes con ack manual
**Requerimiento:** RNF 12–14 y la robustez ante mensajes mal formados (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Las colas se consumen con `autoAck = false`, y `basicAck` se hace **después** de procesar el mensaje, con `basicQos(1)`. Así cada sede procesa un mensaje a la vez y en orden, lo que el punto 10 necesita. Un mensaje mal formado, sin tipo o dirigido a otra sede se registra en `System.err` y se confirma (ack) igual, para que un mensaje malo no tumbe la sede ni se reintente sin fin. `ProcesadorMensajes` no conoce RabbitMQ (recibe un `Mensaje` y devuelve la respuesta), así que su lógica se prueba sin broker. Para publicar se usa un canal separado protegido con `synchronized`, porque un `Channel` no es seguro entre hilos, y las respuestas usan su propio canal, para que una espera no bloquee su entrega.

### 12. Configuración por `.properties` sin `java.util.Properties`
**Requerimiento:** ejecución en al menos 2 computadoras; restricción de no usar colecciones (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Cada sede tiene su archivo `sedes/sede-COD.properties`, con código, nombre, puerto HTTP, host, puerto, usuario y clave de RabbitMQ, timeout y carpeta de datos. `ConfigSede` lo lee con un lector propio en UTF-8, por dos razones:
- `java.util.Properties` es un `Hashtable`, es decir, una colección.
- `Properties` lee en ISO-8859-1, lo que dañaría "San José" y "Limón".

Para desplegar en otra computadora se usa `--config` con un archivo externo, y se cambia `rabbit.host` sin recompilar. La variable de entorno `CONVITEC_RABBIT_CLAVE` reemplaza la clave, para que la clave real no se suba al repositorio.

### 13. Contratos en `servicios` para desacoplar a las 3 integrantes
**Requerimiento:** POO y separación entre lógica, modelo y persistencia (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Los módulos se conectan solo por interfaces:
- **Contratos que implementa la Persona 2:** `IInventarioLocal`, `ICatalogoGlobal`, `IGestorPedidos` e `IRepositorioVentas`.
- **Servicios para la Persona 1:** `IMensajeria`, `IServicioRutas` e `IServicioEstadisticas`.

Así cada integrante pudo avanzar sin esperar a las otras: mientras el código de la Persona 2 no existe, hay implementaciones de demostración (paquete `demo`) y objetos falsos en las pruebas. Todo se conecta en un solo punto, `Sede.conDemostracion`, donde la Persona 2 cambia tres líneas, y `SedeArranqueTest` verifica que la sede siga arrancando después del cambio. Quedan tres ciclos entre `servicios` y los paquetes que lo implementan; en [arquitectura.md](arquitectura.md#c-paquetes-y-dependencias) se explica su causa y cómo resolverlos.

### 14. Sin colecciones de Java
**Requerimiento:** restricción del enunciado de no usar colecciones de Java ni ordenamientos de librería (número por confirmar). <!-- TODO DI1: actualizar IDs -->

Todo se implementa con arreglos nativos:
- Las estructuras que crecen duplican su tamaño y copian los elementos a mano (`Grafo`, `RepositorioVentasMemoria`, `RespuestasPendientes` y los acumuladores de `ServicioEstadisticas`).
- Las búsquedas son lineales, porque con 13 ubicaciones y las ventas de una sede bastan.
- Gson deserializa a arreglos (`RutaDTO[]`), nunca a `List`.
- Los getters de arreglos devuelven copias, para mantener la inmutabilidad.

La única importación de `java.util` en el código es `java.util.concurrent.TimeoutException`, una excepción del cliente de RabbitMQ que hay que atrapar y que no es una colección.

## Cómo exportar los diagramas

Los diagramas están escritos en Mermaid. GitHub los muestra directamente al abrir los `.md`. Para pasarlos al documento final:

- **mermaid.live**: abrir https://mermaid.live, pegar el contenido de un bloque `mermaid` (sin las líneas con las comillas invertidas) y usar *Actions → PNG* o *SVG* para descargarlo.
- **VS Code**: instalar una extensión de vista previa de Mermaid, por ejemplo *Markdown Preview Mermaid Support*, y abrir la vista previa del `.md` (`Ctrl+Shift+V`, o `Cmd+Shift+V` en Mac). Desde la vista previa se puede copiar o capturar cada diagrama. Otra opción es la extensión *Mermaid Chart*, que también exporta a PNG y SVG.
