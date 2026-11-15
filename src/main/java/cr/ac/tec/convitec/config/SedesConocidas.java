package cr.ac.tec.convitec.config;

/**
 * Las 4 sedes de la cadena: código de 3 letras y nombre (nodo del grafo de rutas).
 *
 *   LIB = Liberia, ALA = Alajuela, SJO = San José, LIM = Limón
 *
 * Solo usa arreglos nativos.
 */
public final class SedesConocidas {

    private static final String[] CODIGOS = {"LIB", "ALA", "SJO", "LIM"};
    private static final String[] NOMBRES = {"Liberia", "Alajuela", "San José", "Limón"};

    private SedesConocidas() {
        // Clase de utilidad: no se instancia
    }

    /** true si el código es LIB, ALA, SJO o LIM. */
    public static boolean esCodigoValido(String codigo) {
        return indiceDe(codigo) != -1;
    }

    /** Nombre de la sede ("Limón" para "LIM"), o el mismo código si no se conoce. */
    public static String nombreDe(String codigo) {
        int i = indiceDe(codigo);
        return i == -1 ? codigo : NOMBRES[i];
    }

    /** Copia de los 4 códigos. */
    public static String[] codigos() {
        String[] copia = new String[CODIGOS.length];
        for (int i = 0; i < CODIGOS.length; i++) {
            copia[i] = CODIGOS[i];
        }
        return copia;
    }

    private static int indiceDe(String codigo) {
        if (codigo == null) {
            return -1;
        }
        for (int i = 0; i < CODIGOS.length; i++) {
            if (CODIGOS[i].equals(codigo)) {
                return i;
            }
        }
        return -1;
    }
}
