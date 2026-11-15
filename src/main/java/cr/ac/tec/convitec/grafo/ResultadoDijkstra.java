package cr.ac.tec.convitec.grafo;

/**
 * Resultado de correr Dijkstra desde UN origen hacia TODOS los vértices.
 *
 * Se calcula una sola vez y se puede consultar muchas veces. El planificador de entregas
 * (Paso 3) lo usa para elegir, entre varios destinos pendientes, el más cercano.
 *
 * - distancias[i] = tiempo mínimo del origen a i, o Grafo.SIN_CONEXION si i es inalcanzable.
 * - previos[i]    = vértice anterior a i en el camino más corto (-1 para el origen o inalcanzables).
 *
 * Solo usa arreglos nativos.
 */
public class ResultadoDijkstra {

    private final Grafo grafo;
    private final int origen;
    private final int[] distancias;
    private final int[] previos;

    ResultadoDijkstra(Grafo grafo, int origen, int[] distancias, int[] previos) {
        this.grafo = grafo;
        this.origen = origen;
        this.distancias = distancias;
        this.previos = previos;
    }

    public int getOrigen() {
        return origen;
    }

    public String getNombreOrigen() {
        return grafo.getNombre(origen);
    }

    public int getCantidadVertices() {
        return distancias.length;
    }

    /** Tiempo mínimo del origen al destino, o Grafo.SIN_CONEXION si no es alcanzable. */
    public int getDistancia(int destino) {
        validarIndice(destino);
        return distancias[destino];
    }

    public boolean esAlcanzable(int destino) {
        return getDistancia(destino) != Grafo.SIN_CONEXION;
    }

    /**
     * Camino más corto como índices, en orden (origen ... destino).
     * Devuelve un arreglo vacío si el destino no es alcanzable.
     */
    public int[] getCaminoIndices(int destino) {
        if (!esAlcanzable(destino)) {
            return new int[0];
        }
        // 1) Contar cuántos vértices tiene el camino siguiendo los previos hacia atrás
        int largo = 0;
        for (int v = destino; v != -1; v = previos[v]) {
            largo++;
        }
        // 2) Llenar el arreglo de atrás hacia adelante para que quede origen ... destino
        int[] camino = new int[largo];
        int pos = largo - 1;
        for (int v = destino; v != -1; v = previos[v]) {
            camino[pos] = v;
            pos--;
        }
        return camino;
    }

    /** Igual que getCaminoIndices, pero con los nombres de las ubicaciones. */
    public String[] getCamino(int destino) {
        int[] indices = getCaminoIndices(destino);
        String[] nombres = new String[indices.length];
        for (int i = 0; i < indices.length; i++) {
            nombres[i] = grafo.getNombre(indices[i]);
        }
        return nombres;
    }

    private void validarIndice(int indice) {
        if (indice < 0 || indice >= distancias.length) {
            throw new GrafoException("Índice de ubicación fuera de rango: " + indice);
        }
    }
}
