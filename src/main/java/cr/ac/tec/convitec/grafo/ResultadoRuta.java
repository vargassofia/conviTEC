package cr.ac.tec.convitec.grafo;

/**
 * Resultado de buscar la ruta más corta entre DOS ubicaciones (por nombre).
 *
 * - Si el destino es alcanzable: tiempoTotal en minutos y camino en orden (origen ... destino).
 * - Si NO es alcanzable (por ejemplo Liberia -> Cartago): alcanzable = false,
 *   tiempoTotal = SIN_RUTA (-1) y camino vacío. No se lanza excepción.
 *
 * Los nombres se guardan como están en el grafo ("San José"), aunque se haya
 * preguntado con otra escritura ("san jose").
 *
 * Es inmutable: getCamino() devuelve una copia del arreglo.
 */
public class ResultadoRuta {

    /** Valor de tiempoTotal cuando no existe ruta. */
    public static final int SIN_RUTA = -1;

    private final String origen;
    private final String destino;
    private final int tiempoTotal;
    private final String[] camino;
    private final boolean alcanzable;

    public ResultadoRuta(String origen, String destino, int tiempoTotal, String[] camino, boolean alcanzable) {
        this.origen = origen;
        this.destino = destino;
        this.alcanzable = alcanzable;
        this.tiempoTotal = alcanzable ? tiempoTotal : SIN_RUTA;
        this.camino = (alcanzable && camino != null) ? copiar(camino) : new String[0];
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    /** Minutos totales del recorrido, o SIN_RUTA (-1) si no es alcanzable. */
    public int getTiempoTotal() {
        return tiempoTotal;
    }

    /** Copia del camino (origen ... destino). Vacío si no es alcanzable. */
    public String[] getCamino() {
        return copiar(camino);
    }

    public boolean esAlcanzable() {
        return alcanzable;
    }

    /** Ejemplo: "Alajuela -> San José -> Cartago (80 min)" o "Liberia -> Cartago: no hay ruta". */
    @Override
    public String toString() {
        if (!alcanzable) {
            return origen + " -> " + destino + ": no hay ruta";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camino.length; i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(camino[i]);
        }
        sb.append(" (").append(tiempoTotal).append(" min)");
        return sb.toString();
    }

    private static String[] copiar(String[] original) {
        String[] copia = new String[original.length];
        for (int i = 0; i < original.length; i++) {
            copia[i] = original[i];
        }
        return copia;
    }
}
