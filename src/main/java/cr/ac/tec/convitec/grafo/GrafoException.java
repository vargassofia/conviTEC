package cr.ac.tec.convitec.grafo;

/**
 * Error propio del módulo de grafo: rutas inválidas, duplicadas,
 * nodos inexistentes o archivos mal formados.
 */
public class GrafoException extends RuntimeException {

    public GrafoException(String mensaje) {
        super(mensaje);
    }

    public GrafoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
