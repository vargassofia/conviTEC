# Diagramas de clases (Persona 3)

Parte del [DI3 — Diseño de la solución](DI3-diseno-solucion.md).

Paquetes: `grafo`, `rutas`, `modelo` (ventas), `entregas`, `estadisticas`, `mensajeria`, `config` y `app`, todos dentro de `cr.ac.tec.convitec`. Se muestran los atributos y métodos públicos principales; se omiten getters triviales cuando no aportan. Las interfaces del paquete `servicios` aparecen como «interface». Las clases de otros paquetes aparecen sin miembros, solo para mostrar la relación.

Relaciones: `..|>` implementa, `*--` contiene (composición), `-->` tiene una referencia, `..>` usa o crea.

## grafo

```mermaid
classDiagram
    class Grafo {
        +int SIN_CONEXION$
        -String[] nombres
        -String[] claves
        -int[][] matriz
        -String[][] idsRuta
        -int cantidadVertices
        -int cantidadAristas
        +Grafo()
        +Grafo(int capacidadInicial)
        +agregarVertice(String nombre) int
        +indiceDe(String nombre) int
        +indiceObligatorio(String nombre) int
        +existeVertice(String nombre) boolean
        +getNombre(int indice) String
        +getNombres() String[]
        +agregarArista(String origen, String destino, int tiempo, String idRuta) void
        +getPeso(int i, int j) int
        +sonAdyacentes(int i, int j) boolean
        +getIdRuta(int i, int j) String
        +getCantidadVertices() int
        +getCantidadAristas() int
    }
    class CargadorGrafo {
        +cargarDesdeArchivo(Path archivo)$ Grafo
        +cargarDesdeRecurso(String nombreRecurso)$ Grafo
        +cargarDesdeTexto(String json)$ Grafo
    }
    class ArchivoRutasDTO {
        RutaDTO[] rutas
    }
    class RutaDTO {
        String id
        String origen
        String destino
        Integer tiempo
    }
    class Dijkstra {
        +calcular(Grafo grafo, int origen)$ ResultadoDijkstra
        +calcular(Grafo grafo, String origen)$ ResultadoDijkstra
        +caminoMasCorto(Grafo grafo, String origen, String destino)$ ResultadoRuta
        +existeRuta(Grafo grafo, String origen, String destino)$ boolean
    }
    class ResultadoDijkstra {
        -int origen
        -int[] distancias
        -int[] previos
        +getDistancia(int destino) int
        +esAlcanzable(int destino) boolean
        +getCamino(int destino) String[]
        +getCaminoIndices(int destino) int[]
        +getNombreOrigen() String
    }
    class ResultadoRuta {
        +int SIN_RUTA$
        -String origen
        -String destino
        -int tiempoTotal
        -String[] camino
        -boolean alcanzable
        +getTiempoTotal() int
        +getCamino() String[]
        +esAlcanzable() boolean
    }
    class GrafoException {
        +GrafoException(String mensaje)
    }
    class RuntimeException

    CargadorGrafo ..> ArchivoRutasDTO : lee con Gson
    ArchivoRutasDTO *-- RutaDTO
    CargadorGrafo ..> Grafo : crea
    Dijkstra ..> Grafo : usa
    Dijkstra ..> ResultadoDijkstra : crea
    Dijkstra ..> ResultadoRuta : crea
    ResultadoDijkstra --> Grafo
    GrafoException --|> RuntimeException
```

## rutas

```mermaid
classDiagram
    class IServicioRutas {
        <<interface>>
        +existeRuta(String origen, String destino) boolean
        +caminoMasCorto(String origen, String destino) ResultadoRuta
        +iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida) void
        +hayEntregasPendientes() boolean
        +calcularSiguienteEntrega() ParadaEntrega
        +obtenerRecorrido() RecorridoEntrega
        +planificarRecorridoCompleto(SolicitudEntrega[] pendientes, String horaSalida) RecorridoEntrega
    }
    class ServicioRutas {
        -Grafo grafo
        -PlanificadorEntregas planificador
        +ServicioRutas(Grafo grafo, String sede)
    }
    class PlanificadorEntregas {
        -Grafo grafo
        -int indiceSede
        -int ubicacionActual
        -int tiempoAcumulado
        -ResultadoDijkstra distanciasDesdeActual
        -String[] idsPendientes
        -int[] destinosPendientes
        -boolean[] entregado
        -ParadaEntrega[] paradas
        -String[] idsNoAlcanzables
        -String[] explicacionesNoAlcanzables
        +PlanificadorEntregas(Grafo grafo, String sede)
        +iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida) void
        +hayEntregasPendientes() boolean
        +calcularSiguienteEntrega() ParadaEntrega
        +getUbicacionActual() String
        +getTiempoAcumulado() int
        +obtenerRecorrido() RecorridoEntrega
        +planificarRecorridoCompleto(SolicitudEntrega[] pendientes, String horaSalida) RecorridoEntrega
    }
    class SolicitudEntrega {
        -String idPedido
        -String destino
        +getIdPedido() String
        +getDestino() String
    }
    class ParadaEntrega {
        -int numero
        -String desde
        -String destino
        -String[] camino
        -int tiempoTramo
        -int tiempoAcumulado
        -String[] idsPedidos
        +getTiempoAcumulado() int
        +getIdsPedidos() String[]
        +getCamino() String[]
    }
    class RecorridoEntrega {
        -String sede
        -String horaSalida
        -ParadaEntrega[] paradas
        -int tiempoTotal
        -int cantidadPedidosEntregados
        -String[] idsNoAlcanzables
        -String[] explicacionesNoAlcanzables
        +getParadas() ParadaEntrega[]
        +getTiempoTotal() int
        +getIdsNoAlcanzables() String[]
        +getExplicacionesNoAlcanzables() String[]
    }
    class SalidasDiarias {
        +String SALIDA_MANANA$
        +String SALIDA_TARDE$
        +salidaParaRegistro(LocalTime horaRegistro)$ Asignacion
        +esHoraSalidaValida(String hora)$ boolean
    }
    class Asignacion {
        -String horaSalida
        -boolean diaSiguiente
        +getHoraSalida() String
        +esDiaSiguiente() boolean
    }
    class Grafo
    class Dijkstra
    class ResultadoDijkstra

    ServicioRutas ..|> IServicioRutas
    ServicioRutas *-- PlanificadorEntregas
    ServicioRutas ..> Dijkstra : existeRuta y caminoMasCorto
    PlanificadorEntregas --> Grafo
    PlanificadorEntregas ..> Dijkstra : una vez por parada
    PlanificadorEntregas --> ResultadoDijkstra
    PlanificadorEntregas ..> SolicitudEntrega : recibe
    PlanificadorEntregas *-- ParadaEntrega
    PlanificadorEntregas ..> RecorridoEntrega : crea
    PlanificadorEntregas ..> SalidasDiarias : valida la hora
    RecorridoEntrega *-- ParadaEntrega
    SalidasDiarias ..> Asignacion : crea
```

`Asignacion` es la clase anidada `SalidasDiarias.Asignacion`.

## modelo (ventas) y entregas

```mermaid
classDiagram
    class Venta {
        -String idVenta
        -String idPedido
        -String sede
        -String destino
        -LocalDateTime fechaHora
        -String horaSalida
        -DetalleVenta[] detalles
        -double montoTotal
        -int tiempoTraslado
        +Venta(String idVenta, String idPedido, String sede, String destino, LocalDateTime fechaHora, String horaSalida, DetalleVenta[] detalles, int tiempoTraslado)
        +getDetalles() DetalleVenta[]
        +getMontoTotal() double
        +getTiempoTraslado() int
        +getRendimiento() double
    }
    class DetalleVenta {
        -String codigoProducto
        -String nombreProducto
        -int cantidad
        -double precioUnitario
        -double subtotal
        +DetalleVenta(String codigoProducto, String nombreProducto, int cantidad, double precioUnitario)
        +getSubtotal() double
    }
    class ServicioEntregas {
        -String sede
        -IGestorPedidos gestor
        -IRepositorioVentas repositorio
        +ServicioEntregas(String sede, IGestorPedidos gestor, IRepositorioVentas repositorio)
        +registrarParada(ParadaEntrega parada, String horaSalida, LocalDateTime fechaHora) Venta[]
        +registrarRecorrido(RecorridoEntrega recorrido, LocalDateTime fechaHora) Venta[]
    }
    class IGestorPedidos {
        <<interface>>
        +obtenerDetalle(String idPedido) DetalleVenta[]
        +marcarEntregado(String idPedido, LocalDateTime fechaHora) void
    }
    class IRepositorioVentas {
        <<interface>>
        +guardar(Venta venta) void
        +listar() Venta[]
        +buscarPorIdPedido(String idPedido) Venta
    }
    class RepositorioVentasMemoria {
        -Venta[] ventas
        -int cantidad
        +getCantidad() int
    }
    class ParadaEntrega
    class RecorridoEntrega

    Venta *-- DetalleVenta
    ServicioEntregas --> IGestorPedidos
    ServicioEntregas --> IRepositorioVentas
    ServicioEntregas ..> ParadaEntrega : recibe
    ServicioEntregas ..> RecorridoEntrega : recibe
    ServicioEntregas ..> Venta : crea
    RepositorioVentasMemoria ..|> IRepositorioVentas
    IGestorPedidos ..> DetalleVenta
    IRepositorioVentas ..> Venta
```

`IGestorPedidos` e `IRepositorioVentas` (paquete `servicios`) los implementa la Persona 2. Por ahora, `RepositorioVentasMemoria` sirve para las pruebas y la demostración.

## estadisticas

```mermaid
classDiagram
    class Comparador~T~ {
        <<interface>>
        +comparar(T a, T b) int
    }
    class Ordenamiento {
        +ordenar(T[] datos, Comparador~T~ comparador)$ T[]
        -mergeSort(T[] datos, T[] auxiliar, int inicio, int fin, Comparador~T~ comparador)$ void
        -mezclar(T[] datos, T[] auxiliar, int inicio, int medio, int fin, Comparador~T~ comparador)$ void
    }
    class IServicioEstadisticas {
        <<interface>>
        +historialVentas() Venta[]
        +historialVentas(LocalDate desde, LocalDate hasta) Venta[]
        +productoEstrella() ProductoEstrella
        +rankingProductos() ResumenProducto[]
        +peoresPedidos(int cantidad) Venta[]
        +montoTotalVendido() double
    }
    class ServicioEstadisticas {
        -String sede
        -IRepositorioVentas repositorio
        +ServicioEstadisticas(String sede, IRepositorioVentas repositorio)
    }
    class ResumenProducto {
        -String codigoProducto
        -String nombreProducto
        -int unidadesVendidas
        -double montoTotal
    }
    class ProductoEstrella
    class IRepositorioVentas {
        <<interface>>
    }
    class Venta

    ServicioEstadisticas ..|> IServicioEstadisticas
    ServicioEstadisticas --> IRepositorioVentas : lee en cada consulta
    ServicioEstadisticas ..> Ordenamiento : ordena
    ServicioEstadisticas ..> Comparador : 3 comparadores con desempate
    ServicioEstadisticas ..> ResumenProducto : crea
    ServicioEstadisticas ..> Venta
    ProductoEstrella --|> ResumenProducto
    Ordenamiento ..> Comparador
```

Comparadores de `ServicioEstadisticas`: historial (fecha descendente, luego `idVenta`), ranking (monto descendente, luego más unidades, luego código) y peores pedidos (rendimiento ascendente, luego más tiempo, luego `idPedido`).

## mensajeria

```mermaid
classDiagram
    class IMensajeria {
        <<interface>>
        +iniciar() void
        +cerrar() void
        +solicitarReserva(String sedeDestino, String idPedido, String codigoProducto, int cantidad) RespuestaReserva
        +confirmarReserva(String sedeDestino, String idPedido, String codigoProducto) void
        +liberarReserva(String sedeDestino, String idPedido, String codigoProducto) void
        +publicarActualizacionStock(String codigoProducto, int cantidadNueva) void
    }
    class MensajeriaRabbit {
        -ConfigSede config
        -String codigoSede
        -ProcesadorMensajes procesador
        -RespuestasPendientes pendientes
        -TopologiaRabbit topologia
        -Connection conexion
        -Channel canalConsumo
        -Channel canalRespuestas
        -Channel canalPublicacion
        -String colaRespuestas
        +MensajeriaRabbit(ConfigSede config, IInventarioLocal inventario, ICatalogoGlobal catalogo)
        +estaIniciada() boolean
    }
    class ProcesadorMensajes {
        -String codigoSede
        -IInventarioLocal inventario
        -ICatalogoGlobal catalogo
        +procesar(Mensaje mensaje) Mensaje
    }
    class RespuestasPendientes {
        -Entrada[] entradas
        -int cantidad
        +registrar(String correlationId) void
        +esperar(String correlationId, long timeoutMs) Mensaje
        +completar(String correlationId, Mensaje respuesta) boolean
        +cancelar(String correlationId) void
    }
    class Mensaje {
        -String id
        -TipoMensaje tipo
        -String sedeOrigen
        -String sedeDestino
        -String correlationId
        -String idPedido
        -String codigoProducto
        -Integer cantidad
        -boolean exito
        -String motivo
        -String fechaHora
        +solicitarReserva(String origen, String destino, String idPedido, String codigoProducto, int cantidad)$ Mensaje
        +respuestaReserva(Mensaje solicitud, String origen, boolean exito, String motivo)$ Mensaje
        +confirmarReserva(String origen, String destino, String idPedido, String codigoProducto)$ Mensaje
        +liberarReserva(String origen, String destino, String idPedido, String codigoProducto)$ Mensaje
        +actualizacionStock(String origen, String codigoProducto, int cantidadNueva)$ Mensaje
        +aJson() String
        +desdeJson(String json)$ Mensaje
    }
    class TipoMensaje {
        <<enumeration>>
        SOLICITAR_RESERVA
        RESPUESTA_RESERVA
        CONFIRMAR_RESERVA
        LIBERAR_RESERVA
        ACTUALIZACION_STOCK
    }
    class RespuestaReserva {
        -boolean exito
        -String motivo
        -String sede
        +ok(String sede)$ RespuestaReserva
        +rechazo(String sede, String motivo)$ RespuestaReserva
        +isExito() boolean
        +getMotivo() String
    }
    class TopologiaRabbit {
        +String EXCHANGE_DIRECTO$
        +String EXCHANGE_STOCK$
        +TopologiaRabbit PRODUCCION$
        -String sufijo
        +exchangeDirecto() String
        +exchangeStock() String
        +colaSede(String codigoSede) String
        +colaStock(String codigoSede) String
    }
    class IInventarioLocal {
        <<interface>>
        +reservar(String idPedido, String codigoProducto, int cantidad) RespuestaReserva
        +confirmar(String idPedido, String codigoProducto) int
        +liberar(String idPedido, String codigoProducto) void
    }
    class ICatalogoGlobal {
        <<interface>>
        +actualizarStock(String codigoSede, String codigoProducto, int cantidadNueva) void
    }
    class MensajeriaException
    class ConfigSede

    MensajeriaRabbit ..|> IMensajeria
    MensajeriaRabbit *-- ProcesadorMensajes
    MensajeriaRabbit *-- RespuestasPendientes
    MensajeriaRabbit --> TopologiaRabbit
    MensajeriaRabbit --> ConfigSede
    MensajeriaRabbit ..> Mensaje : publica y recibe JSON
    MensajeriaRabbit ..> MensajeriaException : lanza
    ProcesadorMensajes --> IInventarioLocal
    ProcesadorMensajes --> ICatalogoGlobal
    ProcesadorMensajes ..> Mensaje
    RespuestasPendientes ..> Mensaje
    Mensaje --> TipoMensaje
    IMensajeria ..> RespuestaReserva
    IInventarioLocal ..> RespuestaReserva
```

`MensajeriaException` extiende `RuntimeException`. `Entrada` es una clase privada anidada de `RespuestasPendientes` (`correlationId`, `respuesta`, `completada`).

## config y app

```mermaid
classDiagram
    class ConfigSede {
        +String VARIABLE_CLAVE$
        -String codigo
        -String nombre
        -int puertoHttp
        -String rabbitHost
        -int rabbitPuerto
        -String rabbitUsuario
        -String rabbitClave
        -int timeoutReservaMs
        -String carpetaDatos
        +cargarDesdeArchivo(Path archivo)$ ConfigSede
        +cargarDesdeRecurso(String nombreRecurso)$ ConfigSede
        +desdeTexto(String texto, String origen)$ ConfigSede
        +getPuertoHttp() int
        +getCarpetaDatos() String
    }
    class SedesConocidas {
        +esCodigoValido(String codigo)$ boolean
        +nombreDe(String codigo)$ String
        +codigos()$ String[]
    }
    class ConfigException
    class Main {
        +main(String[] args)$ void
    }
    class ArgumentosSede {
        +String USO$
        -String codigoSede
        -Path archivoConfig
        +interpretar(String[] args)$ ArgumentosSede
        +cargarConfiguracion() ConfigSede
    }
    class Sede {
        -ConfigSede config
        -Grafo grafo
        -ServicioRutas servicioRutas
        -IInventarioLocal inventario
        -ICatalogoGlobal catalogo
        -IRepositorioVentas repositorioVentas
        -ServicioEstadisticas estadisticas
        -IMensajeria mensajeria
        +conDemostracion(ConfigSede config)$ Sede
        +iniciar() void
        +cerrar() void
        +getServicioRutas() ServicioRutas
        +getEstadisticas() ServicioEstadisticas
        +getMensajeria() IMensajeria
    }
    class ConsolaSede {
        +String AYUDA$
        -Sede sede
        +ConsolaSede(Sede sede, BufferedReader entrada, PrintStream salida)
        +ejecutar() void
        +procesarLinea(String linea) boolean
    }
    class ComandoConsola {
        -String nombre
        -String[] argumentos
        +interpretar(String linea)$ ComandoConsola
        +getArgumento(int i) String
        +getNombreUbicacion(int i) String
        +getEntero(int i) int
    }
    class ServicioRutas
    class ServicioEstadisticas
    class MensajeriaRabbit
    class IMensajeria {
        <<interface>>
    }
    class IInventarioLocal {
        <<interface>>
    }
    class ICatalogoGlobal {
        <<interface>>
    }
    class IRepositorioVentas {
        <<interface>>
    }
    class InventarioDemo
    class CatalogoGlobalDemo

    Main ..> ArgumentosSede
    Main ..> Sede : conDemostracion
    Main ..> ConsolaSede
    ArgumentosSede ..> ConfigSede : carga
    ArgumentosSede ..> SedesConocidas : valida
    ConfigSede ..> ConfigException : lanza
    Sede *-- ConfigSede
    Sede *-- ServicioRutas
    Sede *-- ServicioEstadisticas
    Sede --> IInventarioLocal
    Sede --> ICatalogoGlobal
    Sede --> IRepositorioVentas
    Sede --> IMensajeria
    MensajeriaRabbit ..|> IMensajeria
    InventarioDemo ..|> IInventarioLocal
    CatalogoGlobalDemo ..|> ICatalogoGlobal
    ConsolaSede --> Sede
    ConsolaSede ..> ComandoConsola
```

`InventarioDemo` y `CatalogoGlobalDemo` (paquete `demo`) son provisionales: la Persona 2 los reemplazará por sus implementaciones en `Sede.conDemostracion`.
