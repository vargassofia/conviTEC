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
Tienen que salir 32 pruebas sin fallos (GrafoTest, CargadorGrafoTest y DijkstraTest).
