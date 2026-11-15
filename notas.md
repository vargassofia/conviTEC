Nota 1
Cada archivo .java debe de empezar con su paquete.
Ejemplo: 
package cr.ac.tec.convitec.app;

public class Main {
    public static void main(String[] args) {
        System.out.println("ConviTEC iniciando...");
    }
}

Nota 2
No es necesario que instalen docker, mi computadora puede ser el broker ya que tengo la instalacion de RabbitMQ en mi computadora. Por lo que en el codigo, la parte de config.properties se apuntara la IP de mi compu(Mac).
En caso de que quieran probarlo sin mi compu, usen mocks(codigo falso) hasta integramos todo.

Nota 3 
siempre usen el mismo prefijo "convitec" en los paquetes 

nota 4 
en la carpeta modelo existe venta, producto, y pedido, estas son clases vacias provisionales, eliminar o editar en caso de ser necesario
(actualizado) Venta y DetalleVenta ya estan implementadas (las hice yo, ver Nota 14). Pedido y Producto siguen vacias y son de la Persona 2.

Nota 5 - Grafo de rutas (Paso 1, paquete cr.ac.tec.convitec.grafo)
El mapa de rutas esta en src/main/resources/rutas.json. Son 13 ubicaciones y 15 rutas, el tiempo va en minutos.
Formato:
{
  "rutas": [
    { "id": "R01", "origen": "Liberia", "destino": "Cañas", "tiempo": 65 },
    { "id": "R05", "origen": "Alajuela", "destino": "San José", "tiempo": 35 }
  ]
}
Clases:
- Grafo: el grafo no dirigido con matriz de adyacencia (int[][]). Guarda los nombres y el id de cada ruta. Tiene indiceDe, existeVertice, getPeso, sonAdyacentes, getNombres, etc.
- CargadorGrafo: lee rutas.json y arma el Grafo. Valida json vacio o mal escrito, campos faltantes, tiempo <= 0, ids repetidos y rutas repetidas.
- RutaDTO y ArchivoRutasDTO: solo sirven para que Gson lea el json (a arreglos, nunca a List).
- GrafoException: el error propio del modulo (ubicacion que no existe, json malo, etc).

Nota 6 - Dijkstra (Paso 2, mismo paquete grafo)
- Dijkstra: calcula la ruta mas corta (implementacion propia, solo arreglos). Tiene caminoMasCorto, existeRuta y calcular.
- ResultadoRuta: lo que devuelve caminoMasCorto: origen, destino, tiempo total, el camino en orden y si es alcanzable.
- ResultadoDijkstra: lo que devuelve calcular, las distancias desde un origen hacia todas las ubicaciones. Lo voy a usar en el planificador de entregas (Paso 3).
Ejemplo de uso (Persona 2 para validar pedidos, Persona 1 para mostrar la ruta en la interfaz):

import cr.ac.tec.convitec.grafo.*;

Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");   // cargarlo una sola vez al iniciar

if (Dijkstra.existeRuta(grafo, "Alajuela", "Limón")) {
    ResultadoRuta ruta = Dijkstra.caminoMasCorto(grafo, "Alajuela", "Limón");
    int minutos = ruta.getTiempoTotal();       // 215
    String[] camino = ruta.getCamino();        // [Alajuela, San José, Cartago, Paraíso, Turrialba, Limón]
    System.out.println(ruta);                  // Alajuela -> San José -> ... -> Limón (215 min)
} else {
    // rechazar el pedido: no hay ruta desde esa sede
}

Nota 7 - Decisiones importantes
- La busqueda ignora tildes, mayusculas y espacios extra: "san jose" y "San José" son lo mismo. El resultado siempre trae el nombre oficial ("San José").
- Grafo.SIN_CONEXION (Integer.MAX_VALUE) significa que no hay ruta directa. No le sumen nada porque se desborda a negativo.
- Si el destino no es alcanzable NO tira excepcion: devuelve esAlcanzable() = false, tiempo -1 y camino vacio. Solo tira GrafoException si la ubicacion no existe o el grafo esta vacio.
- Liberia y Cañas (Guanacaste) estan aisladas del resto, entonces la sede de Liberia solo puede entregar en Liberia y Cañas. Pedidos de Liberia a otro lado se rechazan.
- ResultadoRuta se movio a grafo, y ParadaEntrega y RecorridoEntrega estan en el paquete rutas (vacias, se hacen en el Paso 3). Venta, Producto y Pedido siguen en modelo.

Nota 8 - Como correr las pruebas
En la terminal, en la carpeta del proyecto:
mvn clean test
Tienen que salir 166 pruebas sin fallos (SedeArranqueTest, GrafoTest, CargadorGrafoTest, DijkstraTest, PlanificadorEntregasTest, SalidasDiariasTest, ServicioRutasTest, VentaTest, ServicioEntregasTest, RepositorioVentasMemoriaTest, OrdenamientoTest, ServicioEstadisticasTest, ConfigSedeTest, MensajeTest, ProcesadorMensajesTest, RespuestasPendientesTest, MensajeriaRabbitIntegracionTest, ArgumentosSedeTest, ComandoConsolaTest, ConsolaSedeTest e InventarioDemoTest).
Ojo: las 5 de MensajeriaRabbitIntegracionTest se SALTAN (Skipped) si RabbitMQ no esta corriendo en su compu. Eso no es un error.

Nota 9 - Planificador de entregas (Paso 3, paquete cr.ac.tec.convitec.rutas)
Clases:
- SolicitudEntrega: un pedido pendiente, solo con idPedido y destino. Es lo que recibe el planificador (asi rutas no depende de la clase Pedido).
- ParadaEntrega: una parada del recorrido: numero, desde donde sale, destino, camino, tiempo del tramo, tiempo acumulado desde la sede y los ids de los pedidos entregados ahi.
- RecorridoEntrega: el recorrido completo de una salida: sede, hora de salida, paradas, tiempo total, cantidad de pedidos entregados y los pedidos no alcanzables con su explicacion. Tiene toString para imprimirlo.
- PlanificadorEntregas: hace el recorrido siguiendo el procedimiento del enunciado (Dijkstra desde la ubicacion actual, ir al mas cercano, entregar, repetir).
- SalidasDiarias: las horas de salida "11:00" y "15:00" y en cual salida va un pedido segun la hora en que se registro.
- ServicioRutas: implementa IServicioRutas (en servicios). Es la que deberian usar desde sus modulos.

Nota 10 - Persona 2: como pasar los pedidos al planificador
Conviertan sus pedidos pendientes de la sede a un arreglo de SolicitudEntrega (solo id y destino). Ejemplo:

SolicitudEntrega[] pendientes = new SolicitudEntrega[cantidadPendientes];
for (int i = 0; i < cantidadPendientes; i++) {
    pendientes[i] = new SolicitudEntrega(pedidos[i].getId(), pedidos[i].getDestino());
}

Para saber en que salida va un pedido nuevo:
SalidasDiarias.Asignacion salida = SalidasDiarias.salidaParaRegistro(LocalTime.now());
salida.getHoraSalida();    // "11:00" o "15:00"
salida.esDiaSiguiente();   // true si se registro desde las 15:00

Despues de planificar, el tiempo de traslado de cada pedido es parada.getTiempoAcumulado() de la parada donde aparece su id (sirve para el rendimiento = monto / tiempo).
Si el id esta repetido o el id/destino viene null, tira GrafoException.

Nota 11 - Persona 1: como usarlo en la interfaz

import cr.ac.tec.convitec.grafo.*;
import cr.ac.tec.convitec.rutas.*;

Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
ServicioRutas servicio = new ServicioRutas(grafo, "San José");   // la sede de esta app

Boton "Calcular siguiente punto de entrega" (paso a paso):
servicio.iniciarRecorrido(pendientes, SalidasDiarias.SALIDA_MANANA);   // una vez, al empezar la salida
if (servicio.hayEntregasPendientes()) {
    ParadaEntrega parada = servicio.calcularSiguienteEntrega();
    parada.getDestino();          // "Heredia"
    parada.getCamino();           // [San José, Heredia]
    parada.getTiempoAcumulado();  // 20
}
servicio.obtenerRecorrido();      // lo que se lleva hasta ahora

Mostrar todo el recorrido de una vez:
RecorridoEntrega recorrido = servicio.planificarRecorridoCompleto(pendientes, "15:00");
System.out.println(recorrido);
// Recorrido desde San José (salida 15:00)
//   1. San José -> Heredia | 20 min (acumulado 20) | pedidos: P2, P4
//   2. Heredia -> San José -> Cartago | 65 min (acumulado 85) | pedidos: P3
//   3. Cartago -> Paraíso -> Turrialba -> Limón | 135 min (acumulado 220) | pedidos: P1
// Tiempo total: 220 min | pedidos entregados: 4

Si se llama calcularSiguienteEntrega sin iniciar o cuando ya no hay pendientes, tira IllegalStateException (usen hayEntregasPendientes para habilitar/deshabilitar el boton).

Nota 12 - Decisiones del planificador
- El vehiculo NO regresa a la sede: el tiempo total termina en la ultima entrega.
- El tiempo acumulado de la parada es el tiempo de traslado de esos pedidos (para el rendimiento).
- Si varios pedidos van al mismo destino se entregan en la misma parada.
- Empates: si dos destinos estan a la misma distancia, gana el pedido que llego primero (el que esta antes en el arreglo).
- Dijkstra se vuelve a correr despues de cada entrega desde la ubicacion nueva (una vez por parada).
- Pedido a la misma sede: se entrega en una parada con tramo 0.
- Salidas: antes de las 11:00 va en la de 11:00; de 11:00 a antes de las 15:00 va en la de 15:00; desde las 15:00 va en la de 11:00 del dia siguiente.
- Pedidos con destino inalcanzable (por ejemplo Liberia a Cartago) o con una ubicacion que no existe NO se entregan y NO tiran excepcion: quedan en getIdsNoAlcanzables() con su explicacion en getExplicacionesNoAlcanzables() (ej. "No hay ruta de Liberia a Cartago").
- Sin pedidos pendientes: recorrido sin paradas y tiempo 0.
- La hora de salida tiene que ser "11:00" o "15:00", si no tira IllegalArgumentException.
- No garantiza el recorrido mas corto posible en total, pero eso lo acepta el enunciado.
- IServicioRutas cambio: ahora recibe los pendientes (iniciarRecorrido y planificarRecorridoCompleto) y tiene hayEntregasPendientes y obtenerRecorrido.

Total de pruebas: 54.

Nota 13
Voy a implementar venta y detalle en modelo. usted se queda con pedido y producto. para conectarnos dejo 2 interfaces en servicios: IGestorPedidos (me da el detalle de un pedido y marca un pedido como entregado) y IRepositorioVentas (Guarda las ventas en archivos). Mientras tanto, pruebo versiones en memoria.

Nota 14 - Ventas (Paso 4, paquete cr.ac.tec.convitec.modelo)
- DetalleVenta: un producto vendido: codigoProducto, nombreProducto, cantidad (> 0), precioUnitario (>= 0) y subtotal (cantidad x precio, se calcula solo).
- Venta: idVenta, idPedido, sede, destino, fechaHora (LocalDateTime), horaSalida ("11:00" o "15:00"), detalles, montoTotal (suma de subtotales, se calcula solo), tiempoTraslado (minutos) y getRendimiento() = monto / tiempo.
Las dos validan sus datos y si algo esta mal tiran IllegalArgumentException. No se pueden modificar despues de creadas.
Venta y DetalleVenta son mias. Pedido y Producto siguen siendo de la Persona 2.

Nota 15 - Persona 2: que tiene que implementar
Dos interfaces en el paquete servicios:

1) IGestorPedidos
- DetalleVenta[] obtenerDetalle(String idPedido): los productos del pedido. Si el pedido no existe devuelvan null.
- void marcarEntregado(String idPedido, LocalDateTime fechaHora): pasar el pedido a ENTREGADO.
Ejemplo de obtenerDetalle para un pedido con 2 cafes de ₡1500 y 1 pan de ₡1000:

public DetalleVenta[] obtenerDetalle(String idPedido) {
    Pedido pedido = buscarPedido(idPedido);     // su estructura propia
    if (pedido == null) {
        return null;
    }
    return new DetalleVenta[]{
        new DetalleVenta("C01", "Café", 2, 1500),
        new DetalleVenta("P05", "Pan", 1, 1000)
    };
}
Ojo: no repitan el mismo codigo de producto en un pedido (sumen las cantidades), y no devuelvan un arreglo vacio para un pedido que existe.

2) IRepositorioVentas (guardando las ventas en archivos de la sede)
- void guardar(Venta venta)
- Venta[] listar(): todas las ventas; si no hay, new Venta[0] (nunca null).
- Venta buscarPorIdPedido(String idPedido): null si ese pedido no tiene venta.
Mientras no exista la de archivos, se puede usar RepositorioVentasMemoria (paquete entregas), que hace lo mismo pero en memoria.

Nota 16 - Persona 1: como registrar las entregas desde la interfaz
ServicioEntregas (paquete entregas) convierte los pedidos entregados en ventas.

import cr.ac.tec.convitec.entregas.*;
import cr.ac.tec.convitec.rutas.*;

ServicioRutas rutas = new ServicioRutas(grafo, "San José");
ServicioEntregas entregas = new ServicioEntregas("San José", gestorPedidos, repositorioVentas);

Paso a paso (boton "Calcular siguiente punto de entrega"):
rutas.iniciarRecorrido(pendientes, "11:00");
if (rutas.hayEntregasPendientes()) {
    ParadaEntrega parada = rutas.calcularSiguienteEntrega();
    Venta[] ventas = entregas.registrarParada(parada, "11:00", LocalDateTime.now());
}

Todo de una vez:
RecorridoEntrega recorrido = rutas.planificarRecorridoCompleto(pendientes, "15:00");
Venta[] ventas = entregas.registrarRecorrido(recorrido, LocalDateTime.now());

Si algun pedido no existe, no tiene productos o ya tenia venta, tira IllegalStateException con el mensaje (pueden mostrarlo en la interfaz).
Si la parada, el recorrido o la fecha vienen null, tira IllegalArgumentException.

Nota 17 - Decisiones de ventas
- La venta se crea cuando el pedido se ENTREGA (no cuando se registra).
- idVenta = "V-" + idPedido (ej. pedido P3 -> venta V-P3).
- El tiempo de traslado de la venta es el tiempo acumulado de la parada donde se entrego (desde la sede).
- Rendimiento = monto / tiempo. Si el tiempo es 0 (pedido entregado en la misma sede) se usa 1 minuto para no dividir entre cero.
- No se duplican ventas: si el pedido ya tiene venta, tira IllegalStateException.
- Si un pedido de la parada falla, NO se registra nada de esa parada (primero se revisan todos y despues se guardan). En registrarRecorrido, las paradas anteriores a la que fallo si quedan registradas.
- Un mismo producto no puede venir repetido en los detalles de una venta.

Total de pruebas: 73.

Nota 18 - Estadisticas de la sede (Paso 5, paquete cr.ac.tec.convitec.estadisticas)
- Comparador: interfaz propia para decir como se ordena algo (no usamos java.util.Comparator).
- Ordenamiento: MergeSort propio. Ordena cualquier arreglo con un Comparador y devuelve uno nuevo (no toca el original).
- ResumenProducto: lo que se vendio de un producto en la sede: codigo, nombre, unidades y monto total.
- ProductoEstrella: el producto con mas monto vendido (es el primero del ranking).
- ServicioEstadisticas: implementa IServicioEstadisticas. Saca historial, producto estrella, ranking de productos, peores pedidos y monto total, leyendo las ventas del repositorio cada vez.
IServicioEstadisticas cambio: ya NO usa Producto ni Pedido (las de la Persona 2). Ahora es:
- Venta[] historialVentas()
- Venta[] historialVentas(LocalDate desde, LocalDate hasta)
- ProductoEstrella productoEstrella()
- ResumenProducto[] rankingProductos()
- Venta[] peoresPedidos(int cantidad)
- double montoTotalVendido()

Nota 19 - MergeSort para todo el grupo
Si necesitan ordenar algo (productos, pedidos, lo que sea), pueden usar Ordenamiento en vez de hacer otro. Ejemplo, ordenar productos por precio de menor a mayor:

import cr.ac.tec.convitec.estadisticas.*;

Producto[] ordenados = Ordenamiento.ordenar(productos,
        (a, b) -> Double.compare(a.getPrecio(), b.getPrecio()));

El comparador devuelve negativo si a va antes, 0 si son iguales y positivo si a va despues. Para orden al reves, inviertan a y b.
Es estable: si dos son iguales quedan en el mismo orden en que venian. Si le pasan un arreglo null tira IllegalArgumentException.

Nota 20 - Persona 1: mostrar las estadisticas en la interfaz

import cr.ac.tec.convitec.estadisticas.*;

ServicioEstadisticas estadisticas = new ServicioEstadisticas("San José", repositorioVentas);   // el mismo repositorio que usa ServicioEntregas

Historial:
for (Venta v : estadisticas.historialVentas()) {          // o historialVentas(desde, hasta)
    // v.getFechaHora(), v.getDetalles() (productos, cantidades, precios), v.getMontoTotal()
}

Producto estrella:
ProductoEstrella estrella = estadisticas.productoEstrella();
if (estrella == null) {
    // mostrar "Todavia no hay ventas"
} else {
    // estrella.getNombreProducto(), estrella.getUnidadesVendidas(), estrella.getMontoTotal()
}

Peores pedidos:
Venta[] peores = estadisticas.peoresPedidos(5);            // los 5 de menor rendimiento, el peor primero
// peores[i].getIdPedido(), peores[i].getRendimiento() (₡ por minuto), peores[i].getTiempoTraslado()

Nota 21 - Decisiones de estadisticas
- Producto estrella = el de MAYOR MONTO (unidades x precio), no el de mas unidades.
- Empates en producto estrella y ranking: mayor monto, si empatan el de mas unidades, si siguen empatados el codigo menor alfabeticamente.
- Empates en peores pedidos: menor rendimiento, si empatan el de mas tiempo de traslado, si siguen empatados el idPedido menor.
- Si no hay ventas, productoEstrella() devuelve null (y el resto devuelve arreglos vacios o 0).
- Rendimiento = monto / tiempo. Tiempo 0 (entrega en la misma sede) cuenta como 1 minuto.
- Historial: de la venta mas reciente a la mas antigua (si tienen la misma fecha, idVenta menor primero). El rango de fechas incluye los dos dias; si desde es despues de hasta tira IllegalArgumentException.
- peoresPedidos(0) o negativo tira IllegalArgumentException; si piden mas de los que hay, devuelve todos.
- Se ignoran las ventas de otra sede que esten en el repositorio (cada sede solo ve las suyas).

Total de pruebas: 96.


Nota 22 - Mensajeria entre sedes (Paso 6, paquetes cr.ac.tec.convitec.mensajeria y config)
Las sedes NO se leen archivos entre si: todo lo que una sede necesita de otra va por mensajes RabbitMQ.
Flujo de un pedido (ejemplo: Alajuela pide 2 bebidas B01 que solo hay en Limon):

  ALA                                        LIM
   |--- SOLICITAR_RESERVA (P7, B01, 2) ------->|  reservar(): aparta 2 unidades
   |<-- RESPUESTA_RESERVA (exito / motivo) ----|
   |
   |  si TODO el pedido se puede y hay ruta:
   |--- CONFIRMAR_RESERVA (P7, B01) ---------->|  confirmar(): descuenta definitivo
   |                                           |--- ACTUALIZACION_STOCK (B01 = 3) ---> TODAS las sedes
   |  si algo falla:
   |--- LIBERAR_RESERVA (P7, B01) ------------>|  liberar(): devuelve las 2 unidades

Topologia en RabbitMQ:
- Exchange directo "convitec.directo": cada sede tiene su cola "convitec.sede.LIB", "convitec.sede.ALA", etc. Ahi le llegan SOLICITAR, CONFIRMAR y LIBERAR.
- Exchange fanout "convitec.stock": cada sede tiene su cola "convitec.stock.<COD>". Ahi llegan las ACTUALIZACION_STOCK de todas.
- Cada sede tiene ademas una cola de respuestas temporal (la crea RabbitMQ con un nombre raro tipo amq.gen-...).
Las colas son durables: si una sede esta apagada, sus mensajes la esperan en la cola.

Los mensajes viajan en JSON. Ejemplo de una solicitud:
{
  "id": "ALA-1763140000000-1-48213",
  "tipo": "SOLICITAR_RESERVA",
  "sedeOrigen": "ALA",
  "sedeDestino": "LIM",
  "correlationId": "ALA-1763140000000-1-48213",
  "idPedido": "P7",
  "codigoProducto": "B01",
  "cantidad": 2,
  "exito": false,
  "fechaHora": "2026-11-15T10:30:00.123"
}
Tipos: SOLICITAR_RESERVA, RESPUESTA_RESERVA, CONFIRMAR_RESERVA, LIBERAR_RESERVA y ACTUALIZACION_STOCK.
Las sedes se nombran por codigo: LIB (Liberia), ALA (Alajuela), SJO (San José), LIM (Limón).

Clases:
- MensajeriaRabbit: implementa IMensajeria (la que usan ustedes). Conecta, envia y recibe.
- ProcesadorMensajes: decide que hacer con cada mensaje que llega (no sabe nada de RabbitMQ).
- RespuestasPendientes: hace que solicitarReserva espere la respuesta (con wait/notify y timeout).
- Mensaje, TipoMensaje, RespuestaReserva, TopologiaRabbit, MensajeriaException.
- config.ConfigSede: la configuracion de la sede (ver Nota 24). config.SedesConocidas: los 4 codigos y nombres.

Nota 23 - Persona 2: que tiene que implementar para la mensajeria
1) IInventarioLocal (paquete servicios): lo llama la mensajeria cuando OTRA sede le pide algo a la suya.
- RespuestaReserva reservar(String idPedido, String codigoProducto, int cantidad): apartar unidades. Devuelvan RespuestaReserva.ok(null) o RespuestaReserva.rechazo(null, "stock insuficiente de B01 (hay 1, se piden 2)"). La mensajeria le agrega el nombre de la sede al motivo.
- int confirmar(String idPedido, String codigoProducto): descontar definitivo lo reservado y devolver el stock nuevo.
- void liberar(String idPedido, String codigoProducto): devolver lo reservado.
MUY IMPORTANTE:
- confirmar y liberar tienen que ser IDEMPOTENTES: si los llaman 2 veces, o sobre una reserva que no existe, no tiene que fallar ni descontar 2 veces.
- Los metodos tienen que ser synchronized: los mensajes llegan en otro hilo mientras la interfaz tambien usa el inventario.

2) ICatalogoGlobal (paquete servicios):
- void actualizarStock(String codigoSede, String codigoProducto, int cantidadNueva): guardar el stock nuevo de esa sede. Tambien synchronized.

Ejemplo de como su servicio de pedidos usa la mensajeria (pedido de Alajuela con productos de Limon y San Jose):

RespuestaReserva r1 = mensajeria.solicitarReserva("LIM", "P7", "B01", 2);
RespuestaReserva r2 = mensajeria.solicitarReserva("SJO", "P7", "C03", 1);

if (r1.isExito() && r2.isExito() && hayRutaAlDestino) {
    mensajeria.confirmarReserva("LIM", "P7", "B01");
    mensajeria.confirmarReserva("SJO", "P7", "C03");
    // registrar el pedido como aceptado
} else {
    // cancelar TODO el pedido: liberar lo que si se habia reservado
    if (r1.isExito()) mensajeria.liberarReserva("LIM", "P7", "B01");
    if (r2.isExito()) mensajeria.liberarReserva("SJO", "P7", "C03");
    String motivo = !r1.isExito() ? r1.getMotivo() : r2.getMotivo();   // ej. "Limón no respondió a tiempo"
    // rechazar el pedido mostrando el motivo
}

Notas:
- solicitarReserva ESPERA la respuesta (hasta reserva.timeoutMs). Si la otra sede no responde, devuelve exito=false con "<Sede> no respondió a tiempo". No tira excepcion por problemas de red.
- Si se vencio el tiempo, igual llamen liberarReserva por si la otra sede si alcanzo a reservar: el orden de la cola garantiza que el LIBERAR se procesa despues de la solicitud.
- Cuando su sede vende o cambia su propio stock, avisen a las demas con mensajeria.publicarActualizacionStock("B01", stockNuevo). (Al confirmar una reserva de otra sede, la mensajeria lo publica sola.)
- Codigo de sede invalido, cantidad <= 0 o mandarse mensajes a si misma tira IllegalArgumentException.

Nota 24 - Configuracion de cada sede
Cada sede tiene su archivo en src/main/resources/sedes/: sede-LIB.properties, sede-ALA.properties, sede-SJO.properties y sede-LIM.properties.

sede.codigo=ALA
sede.nombre=Alajuela
http.puerto=8082              (LIB 8081, ALA 8082, SJO 8083, LIM 8084)
rabbit.host=localhost
rabbit.puerto=5672
rabbit.usuario=guest
rabbit.clave=guest
reserva.timeoutMs=5000
datos.carpeta=datos/ALA

Para iniciar la mensajeria de una sede:

ConfigSede config = ConfigSede.cargarDesdeRecurso("sedes/sede-ALA.properties");
IMensajeria mensajeria = new MensajeriaRabbit(config, inventarioLocal, catalogoGlobal);
mensajeria.iniciar();      // si RabbitMQ no esta, tira MensajeriaException diciendo host y puerto
...
mensajeria.cerrar();       // al apagar la sede

La clave real NO se sube al repositorio: si existe la variable de entorno CONVITEC_RABBIT_CLAVE, reemplaza a rabbit.clave.
Mac/Linux:  export CONVITEC_RABBIT_CLAVE=la_clave
Windows:    set CONVITEC_RABBIT_CLAVE=la_clave     (PowerShell: $env:CONVITEC_RABBIT_CLAVE="la_clave")

Nota 25 - Conectarse desde otra computadora al broker de mi Mac
1. Estar en la MISMA red (mejor un hotspot del celular, porque la red del TEC puede bloquear el puerto).
2. En su .properties poner en rabbit.host la IP de mi Mac (yo se la paso; en la Mac se ve con: ipconfig getifaddr en0).
3. El usuario guest SOLO funciona desde localhost. Yo creo un usuario para el grupo en mi Mac:
   rabbitmqctl add_user convitec la_clave
   rabbitmqctl set_permissions -p / convitec ".*" ".*" ".*"
   y ustedes ponen rabbit.usuario=convitec y la clave en CONVITEC_RABBIT_CLAVE.
4. El puerto 5672 tiene que estar abierto en mi Mac (si el firewall de macOS pregunta, permitir conexiones entrantes).
5. Prueba rapida desde Windows: Test-NetConnection <IP> -Port 5672 (en PowerShell). Si sale TcpTestSucceeded : True, ya llega.

Nota 26 - Pruebas de mensajeria
- ConfigSedeTest, MensajeTest, ProcesadorMensajesTest y RespuestasPendientesTest NO necesitan RabbitMQ.
- MensajeriaRabbitIntegracionTest levanta ALA y LIM de verdad contra RabbitMQ en localhost:5672. Si RabbitMQ no esta corriendo, esas pruebas se SALTAN solas (salen como Skipped, no como fallo).
- Para correrlas en Mac: brew services start rabbitmq. Usan colas con sufijo ".prueba-..." y las borran al final, asi que no ensucian el broker.

Total de pruebas: 128.


Nota 27 - Correr una sede con la consola (mientras la interfaz no esta lista)
1. Encender RabbitMQ en la compu del broker (mi Mac): brew services start rabbitmq
2. Generar el jar: mvn clean package   (queda en target/convitec-sede.jar)
3. Correr cada sede en su propia terminal:
   java -jar target/convitec-sede.jar ALA
   java -jar target/convitec-sede.jar LIM --config C:\convitec\sede-LIM.properties   (desde otra compu, ver README)
4. Escribir "ayuda" para ver los comandos. Ejemplos:
   reservar LIM P1 B01 2
   confirmar LIM P1 B01
   catalogo
   ruta San_José          (o ruta "San José"; las tildes son opcionales)
   entregar 11:00 P1:Heredia P2:Cartago
   salir
Un comando mal escrito muestra el error y la ayuda, pero NO cierra la sede.
El paso a paso para conectar 2 computadoras (rabbitmq-env.conf, usuario convitec, IP, firewall, hotspot) esta en el README.

Nota 28 - Persona 2: el paquete demo es PROVISIONAL
InventarioDemo y CatalogoGlobalDemo (paquete demo) son de mentira: productos fijos en memoria, sin archivos. Estan solo para poder probar la mensajeria y la consola.
Cuando tengan sus clases reales, cambien SOLO la linea del return en Sede.conDemostracion (app/Sede.java):

return new Sede(config, InventarioDemo.paraSede(config.getCodigo()), new CatalogoGlobalDemo(),
        new RepositorioVentasMemoria());

por algo como:

return new Sede(config, new SuInventario(config), new SuCatalogoGlobal(config),
        new SuRepositorioVentas(config));

(los nombres de sus clases los eligen ustedes; config.getCarpetaDatos() les dice en que carpeta guardar los archivos de la sede).
Despues de eso se puede borrar el paquete demo. Ojo: los comandos stock, agregar y catalogo de la consola solo funcionan con las clases demo; con las reales dicen que no estan disponibles.

Nota 29 - Persona 1: arrancar el servidor HTTP
La sede arranca en app/Main.java: lee los argumentos, carga la configuracion, crea la Sede con Sede.conDemostracion(config) y llama sede.iniciar().
Arranquen su servidor HTTP ahi mismo, DESPUES de sede.iniciar(), usando el puerto de la configuracion:

int puerto = config.getPuertoHttp();      // LIB 8081, ALA 8082, SJO 8083, LIM 8084

Todo lo que necesitan lo dan los getters de Sede: getServicioRutas(), getEstadisticas(), getMensajeria(), getInventario(), getCatalogo(), getRepositorioVentas(), getNombre() y getCodigo().
Si quieren dejar la consola tambien, pueden correr el servidor en otro hilo. Para cerrar, sede.cerrar() se puede llamar varias veces sin problema (Main ya lo llama en un shutdown hook).

Total de pruebas: 163 (las 5 de MensajeriaRabbitIntegracionTest salen como Skipped si RabbitMQ esta apagado).


Nota 30 - Huecos marcados y PENDIENTES.md
- Lo que le toca a cada una esta marcado en el codigo con "// TODO PERSONA 1:" o "// TODO PERSONA 2:". Para verlos todos: grep -rn "TODO PERSONA" src
- La lista completa de lo terminado y lo pendiente esta en PENDIENTES.md (en la raiz).
- Cambio respecto a la Nota 28: ahora en Sede.conDemostracion hay 3 lineas separadas (inventario, catalogo y repositorio de ventas), cada una con su TODO PERSONA 2. Cambien esas 3 lineas, no el return.
- SedeArranqueTest arranca las 4 sedes con lo que diga Sede.conDemostracion y una mensajeria falsa (sin RabbitMQ). Si despues de cambiar las clases demo esa prueba falla, el cambio rompio el arranque.

Total de pruebas: 166.
