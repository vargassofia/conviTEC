package cr.ac.tec.convitec.grafo;

/**
 * Algoritmo de Dijkstra (implementación propia) sobre el Grafo con matriz de adyacencia.
 *
 * Versión O(V²) con solo arreglos: dist[], previo[] y visitado[]. No usa heap ni colecciones.
 * Con ~13 ubicaciones esta versión es simple y más que suficiente.
 *
 * Importante: SIN_CONEXION es Integer.MAX_VALUE. Nunca se le suma nada, porque se
 * desbordaría a un número negativo. Siempre se revisa antes de sumar.
 */
public final class Dijkstra {

    private Dijkstra() {
        // Clase de utilidad: no se instancia
    }

    // ------------------------------------------------------------------
    // Origen -> todos los destinos
    // ------------------------------------------------------------------

    /**
     * Calcula los tiempos mínimos y los caminos desde el origen hacia TODOS los vértices.
     *
     * @throws GrafoException si el grafo está vacío o el índice de origen no existe.
     */
    public static ResultadoDijkstra calcular(Grafo grafo, int origen) {
        validarGrafo(grafo);
        int n = grafo.getCantidadVertices();
        if (origen < 0 || origen >= n) {
            throw new GrafoException("Índice de origen fuera de rango: " + origen);
        }

        int[] dist = new int[n];
        int[] previo = new int[n];
        boolean[] visitado = new boolean[n];
        for (int i = 0; i < n; i++) {
            dist[i] = Grafo.SIN_CONEXION;
            previo[i] = -1;
        }
        dist[origen] = 0;

        for (int paso = 0; paso < n; paso++) {
            // 1) Elegir el vértice no visitado con menor distancia
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!visitado[i] && (u == -1 || dist[i] < dist[u])) {
                    u = i;
                }
            }
            // Si el más cercano es inalcanzable, todos los que quedan también lo son
            if (u == -1 || dist[u] == Grafo.SIN_CONEXION) {
                break;
            }
            visitado[u] = true;

            // 2) Relajar los vecinos de u
            for (int v = 0; v < n; v++) {
                if (visitado[v] || !grafo.sonAdyacentes(u, v)) {
                    continue;
                }
                // Aquí dist[u] y el peso son finitos: la suma es segura
                int nuevaDistancia = dist[u] + grafo.getPeso(u, v);
                if (nuevaDistancia < dist[v]) {
                    dist[v] = nuevaDistancia;
                    previo[v] = u;
                }
            }
        }
        return new ResultadoDijkstra(grafo, origen, dist, previo);
    }

    /** Igual que calcular(grafo, int), pero con el nombre de la ubicación de origen. */
    public static ResultadoDijkstra calcular(Grafo grafo, String origen) {
        validarGrafo(grafo);
        return calcular(grafo, grafo.indiceObligatorio(origen));
    }

    // ------------------------------------------------------------------
    // Origen -> un destino (por nombre)
    // ------------------------------------------------------------------

    /**
     * Ruta más corta entre dos ubicaciones.
     * Si el destino no es alcanzable devuelve alcanzable = false (no lanza excepción).
     *
     * @throws GrafoException si el grafo está vacío o alguna ubicación no existe.
     */
    public static ResultadoRuta caminoMasCorto(Grafo grafo, String origen, String destino) {
        validarGrafo(grafo);
        int i = grafo.indiceObligatorio(origen);
        int j = grafo.indiceObligatorio(destino);

        ResultadoDijkstra resultado = calcular(grafo, i);
        boolean alcanzable = resultado.esAlcanzable(j);
        return new ResultadoRuta(
                grafo.getNombre(i),
                grafo.getNombre(j),
                alcanzable ? resultado.getDistancia(j) : ResultadoRuta.SIN_RUTA,
                resultado.getCamino(j),
                alcanzable);
    }

    /**
     * Indica si se puede llegar del origen al destino (directo o pasando por otras ubicaciones).
     *
     * @throws GrafoException si el grafo está vacío o alguna ubicación no existe.
     */
    public static boolean existeRuta(Grafo grafo, String origen, String destino) {
        return caminoMasCorto(grafo, origen, destino).esAlcanzable();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static void validarGrafo(Grafo grafo) {
        if (grafo == null || grafo.getCantidadVertices() == 0) {
            throw new GrafoException("El grafo está vacío: no hay ubicaciones para calcular rutas.");
        }
    }
}
