package cr.ac.tec.convitec.app;

/**
 * Una línea de la consola ya separada en comando y argumentos.
 *
 *   reservar LIM P7 B01 2       -> nombre "reservar", argumentos [LIM, P7, B01, 2]
 *   ruta "San José"             -> nombre "ruta", argumentos [San José]
 *   ruta San_José               -> nombre "ruta", argumentos [San_José]  (getNombreUbicacion la convierte en "San José")
 *
 * - Los espacios extra se ignoran.
 * - Un texto entre comillas dobles es UN solo argumento (para nombres con espacio).
 * - El nombre del comando se pasa a minúsculas.
 * - Una línea vacía da un comando vacío (esVacio() = true).
 *
 * Es inmutable y solo usa arreglos.
 */
public final class ComandoConsola {

    private final String nombre;
    private final String[] argumentos;

    private ComandoConsola(String nombre, String[] argumentos) {
        this.nombre = nombre;
        this.argumentos = argumentos;
    }

    /**
     * @throws IllegalArgumentException si hay una comilla sin cerrar.
     */
    public static ComandoConsola interpretar(String linea) {
        if (linea == null) {
            return new ComandoConsola("", new String[0]);
        }
        // Peor caso: una palabra por cada carácter
        String[] partes = new String[linea.length()];
        int cantidad = 0;
        StringBuilder actual = new StringBuilder();
        boolean enComillas = false;
        boolean hayPalabra = false;

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                enComillas = !enComillas;
                hayPalabra = true; // "" cuenta como argumento vacío
            } else if (Character.isWhitespace(c) && !enComillas) {
                if (hayPalabra) {
                    partes[cantidad++] = actual.toString();
                    actual.setLength(0);
                    hayPalabra = false;
                }
            } else {
                actual.append(c);
                hayPalabra = true;
            }
        }
        if (enComillas) {
            throw new IllegalArgumentException("Hay una comilla sin cerrar.");
        }
        if (hayPalabra) {
            partes[cantidad++] = actual.toString();
        }
        if (cantidad == 0) {
            return new ComandoConsola("", new String[0]);
        }
        String[] args = new String[cantidad - 1];
        for (int i = 1; i < cantidad; i++) {
            args[i - 1] = partes[i];
        }
        return new ComandoConsola(partes[0].toLowerCase(), args);
    }

    /** Nombre del comando en minúsculas ("" si la línea estaba vacía). */
    public String getNombre() {
        return nombre;
    }

    public boolean esVacio() {
        return nombre.isEmpty();
    }

    public int getCantidadArgumentos() {
        return argumentos.length;
    }

    /** Copia de los argumentos. */
    public String[] getArgumentos() {
        String[] copia = new String[argumentos.length];
        for (int i = 0; i < argumentos.length; i++) {
            copia[i] = argumentos[i];
        }
        return copia;
    }

    /** @throws IllegalArgumentException si no existe ese argumento. */
    public String getArgumento(int i) {
        if (i < 0 || i >= argumentos.length) {
            throw new IllegalArgumentException("Falta el argumento #" + (i + 1) + " de '" + nombre + "'.");
        }
        return argumentos[i];
    }

    /** El argumento como nombre de ubicación: el guion bajo cuenta como espacio ("San_José" -> "San José"). */
    public String getNombreUbicacion(int i) {
        return comoUbicacion(getArgumento(i));
    }

    /** "San_José" -> "San José". */
    public static String comoUbicacion(String texto) {
        return texto == null ? null : texto.replace('_', ' ');
    }

    /** @throws IllegalArgumentException si no es un número entero. */
    public int getEntero(int i) {
        String texto = getArgumento(i);
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + texto + "' no es un número entero.");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(nombre);
        for (int i = 0; i < argumentos.length; i++) {
            sb.append(" [").append(argumentos[i]).append(']');
        }
        return sb.toString();
    }
}
