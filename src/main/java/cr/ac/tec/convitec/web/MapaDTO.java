package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.config.SedesConocidas;
import cr.ac.tec.convitec.estadisticas.Ordenamiento;
import cr.ac.tec.convitec.grafo.Dijkstra;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.ResultadoDijkstra;

/**
 * Lo que necesita el mapa de la interfaz (GET /api/grafo): ubicaciones con coordenadas y rutas del grafo.
 *
 * {
 *   "sede": "San José",
 *   "ubicaciones": [ { "nombre": "San José", "lat": 9.92, "lng": -84.09, "codigoSede": "SJO",
 *                      "esEstaSede": true, "alcanzable": true, "componente": 0 }, ... ],
 *   "rutas": [ { "id": "R05", "origen": "Alajuela", "destino": "San José", "tiempo": 35 }, ... ],
 *   "sinCoordenadas": [ ]
 * }
 *
 * - alcanzable: si el vehículo de ESTA sede puede llegar ahí (Dijkstra desde la sede).
 * - componente: número de la parte del grafo a la que pertenece. Las ubicaciones con el mismo número
 *   están conectadas entre sí; la de Liberia y Cañas queda separada (el mapa la marca como zona aislada).
 * - sinCoordenadas: ubicaciones del grafo que no están en ubicaciones.json (no se pueden dibujar).
 * - Las ubicaciones vienen en orden alfabético (MergeSort propio), así la interfaz no tiene que ordenar.
 *
 * Se calcula una sola vez: el grafo no cambia mientras la sede está abierta.
 */
final class MapaDTO {

    final String sede;
    final UbicacionDTO[] ubicaciones;
    final RutaDTO[] rutas;
    final String[] sinCoordenadas;

    private MapaDTO(String sede, UbicacionDTO[] ubicaciones, RutaDTO[] rutas, String[] sinCoordenadas) {
        this.sede = sede;
        this.ubicaciones = ubicaciones;
        this.rutas = rutas;
        this.sinCoordenadas = sinCoordenadas;
    }

    static final class UbicacionDTO {
        final String nombre;
        final double lat;
        final double lng;
        final String codigoSede;   // null si no es sede
        final boolean esEstaSede;
        final boolean alcanzable;
        final int componente;

        UbicacionDTO(String nombre, double lat, double lng, String codigoSede, boolean esEstaSede,
                     boolean alcanzable, int componente) {
            this.nombre = nombre;
            this.lat = lat;
            this.lng = lng;
            this.codigoSede = codigoSede;
            this.esEstaSede = esEstaSede;
            this.alcanzable = alcanzable;
            this.componente = componente;
        }
    }

    static final class RutaDTO {
        final String id;
        final String origen;
        final String destino;
        final int tiempo;

        RutaDTO(String id, String origen, String destino, int tiempo) {
            this.id = id;
            this.origen = origen;
            this.destino = destino;
            this.tiempo = tiempo;
        }
    }

    /**
     * Arma el mapa a partir del grafo y de las coordenadas.
     *
     * @param nombreSede nombre de esta sede en el grafo ("San José")
     */
    static MapaDTO crear(Grafo grafo, CargadorUbicaciones.Ubicacion[] coordenadas, String nombreSede) {
        int n = grafo.getCantidadVertices();
        int indiceSede = grafo.indiceObligatorio(nombreSede);
        ResultadoDijkstra desdeSede = Dijkstra.calcular(grafo, indiceSede);
        int[] componentes = componentes(grafo);

        // Ubicaciones: las que tienen coordenadas van al mapa; las demás, a sinCoordenadas
        int conCoordenadas = 0;
        for (int i = 0; i < n; i++) {
            if (CargadorUbicaciones.buscar(coordenadas, grafo.getNombre(i)) != null) {
                conCoordenadas++;
            }
        }
        UbicacionDTO[] ubicaciones = new UbicacionDTO[conCoordenadas];
        String[] sinCoordenadas = new String[n - conCoordenadas];
        int u = 0;
        int s = 0;
        for (int i = 0; i < n; i++) {
            String nombre = grafo.getNombre(i);
            CargadorUbicaciones.Ubicacion c = CargadorUbicaciones.buscar(coordenadas, nombre);
            if (c == null) {
                sinCoordenadas[s++] = nombre;
            } else {
                ubicaciones[u++] = new UbicacionDTO(nombre, c.lat, c.lng, codigoDeSede(nombre), i == indiceSede,
                        desdeSede.esAlcanzable(i), componentes[i]);
            }
        }

        // Rutas: cada arista una sola vez (la matriz es simétrica, se recorre solo j > i)
        RutaDTO[] rutas = new RutaDTO[grafo.getCantidadAristas()];
        int r = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (grafo.sonAdyacentes(i, j)) {
                    rutas[r++] = new RutaDTO(grafo.getIdRuta(i, j), grafo.getNombre(i), grafo.getNombre(j),
                            grafo.getPeso(i, j));
                }
            }
        }
        // En orden alfabético (sin tildes), para las listas de la interfaz. MergeSort propio del proyecto.
        UbicacionDTO[] ordenadas = Ordenamiento.ordenar(ubicaciones,
                (a, b) -> CargadorUbicaciones.normalizar(a.nombre).compareTo(CargadorUbicaciones.normalizar(b.nombre)));
        return new MapaDTO(grafo.getNombre(indiceSede), ordenadas, rutas, sinCoordenadas);
    }

    /** Código de sede (LIB, ALA, SJO, LIM) si el nombre es una sede; null si no. */
    private static String codigoDeSede(String nombre) {
        String clave = CargadorUbicaciones.normalizar(nombre);
        String[] codigos = SedesConocidas.codigos();
        for (int i = 0; i < codigos.length; i++) {
            if (CargadorUbicaciones.normalizar(SedesConocidas.nombreDe(codigos[i])).equals(clave)) {
                return codigos[i];
            }
        }
        return null;
    }

    /**
     * Componentes conexas con un recorrido en anchura (BFS) sobre la matriz de adyacencia.
     * La cola es un arreglo propio: cada vértice entra una sola vez, así que basta con tamaño n.
     *
     * @return componente[i] = número de la componente del vértice i (0, 1, 2...)
     */
    static int[] componentes(Grafo grafo) {
        int n = grafo.getCantidadVertices();
        int[] componente = new int[n];
        for (int i = 0; i < n; i++) {
            componente[i] = -1;
        }
        int[] cola = new int[n];
        int siguienteNumero = 0;
        for (int inicio = 0; inicio < n; inicio++) {
            if (componente[inicio] != -1) {
                continue;
            }
            int frente = 0;
            int fin = 0;
            cola[fin++] = inicio;
            componente[inicio] = siguienteNumero;
            while (frente < fin) {
                int actual = cola[frente++];
                for (int v = 0; v < n; v++) {
                    if (componente[v] == -1 && grafo.sonAdyacentes(actual, v)) {
                        componente[v] = siguienteNumero;
                        cola[fin++] = v;
                    }
                }
            }
            siguienteNumero++;
        }
        return componente;
    }
}
