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
Tienen que salir 54 pruebas sin fallos (GrafoTest, CargadorGrafoTest, DijkstraTest, PlanificadorEntregasTest, SalidasDiariasTest y ServicioRutasTest).

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
