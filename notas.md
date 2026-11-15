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
Tienen que salir 96 pruebas sin fallos (GrafoTest, CargadorGrafoTest, DijkstraTest, PlanificadorEntregasTest, SalidasDiariasTest, ServicioRutasTest, VentaTest, ServicioEntregasTest, RepositorioVentasMemoriaTest, OrdenamientoTest y ServicioEstadisticasTest).

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
