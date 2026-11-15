package cr.ac.tec.convitec.grafo;

import java.text.Normalizer;

/**
 * Grafo no dirigido y ponderado implementado con MATRIZ DE ADYACENCIA.
 *
 * - Cada vértice es una ubicación (sede o punto de entrega).
 * - matriz[i][j] = tiempo en minutos de la ruta entre i y j.
 * - SIN_CONEXION indica que no existe ruta directa entre i y j.
 * - La diagonal (i == i) vale 0.
 *
 * Solo usa arreglos nativos (permitidos por el enunciado); no usa colecciones de Java.
 */
public class Grafo {

    /** Valor que indica "no hay ruta directa". Cuidado al sumarlo en Dijkstra (desbordamiento). */
    public static final int SIN_CONEXION = Integer.MAX_VALUE;

    private static final int CAPACIDAD_INICIAL = 8;

    private String[] nombres;     // nombre para mostrar: "San José"
    private String[] claves;      // nombre normalizado para buscar: "san jose"
    private int[][] matriz;       // tiempos
    private String[][] idsRuta;   // id de la ruta entre i y j ("R05"), para mostrar en la interfaz
    private int cantidadVertices;
    private int cantidadAristas;

    public Grafo() {
        this(CAPACIDAD_INICIAL);
    }

    public Grafo(int capacidadInicial) {
        if (capacidadInicial <= 0) {
            capacidadInicial = CAPACIDAD_INICIAL;
        }
        nombres = new String[capacidadInicial];
        claves = new String[capacidadInicial];
        matriz = crearMatriz(capacidadInicial);
        idsRuta = new String[capacidadInicial][capacidadInicial];
        cantidadVertices = 0;
        cantidadAristas = 0;
    }

    // ------------------------------------------------------------------
    // Vértices
    // ------------------------------------------------------------------

    /**
     * Agrega una ubicación. Si ya existe, no la duplica y devuelve su índice.
     */
    public int agregarVertice(String nombre) {
        String clave = normalizar(nombre);
        if (clave.isEmpty()) {
            throw new GrafoException("El nombre de la ubicación no puede estar vacío.");
        }
        int existente = buscarPorClave(clave);
        if (existente != -1) {
            return existente;
        }
        if (cantidadVertices == nombres.length) {
            crecer();
        }
        nombres[cantidadVertices] = nombre.trim();
        claves[cantidadVertices] = clave;
        cantidadVertices++;
        return cantidadVertices - 1;
    }

    /**
     * Devuelve el índice de una ubicación o -1 si no existe.
     * La búsqueda ignora mayúsculas, tildes y espacios extra ("san jose" == "San José").
     */
    public int indiceDe(String nombre) {
        return buscarPorClave(normalizar(nombre));
    }

    public boolean existeVertice(String nombre) {
        return indiceDe(nombre) != -1;
    }

    /** Igual que indiceDe, pero lanza una excepción clara si no existe. */
    public int indiceObligatorio(String nombre) {
        int indice = indiceDe(nombre);
        if (indice == -1) {
            throw new GrafoException("La ubicación '" + nombre + "' no existe en el grafo.");
        }
        return indice;
    }

    public String getNombre(int indice) {
        validarIndice(indice);
        return nombres[indice];
    }

    public int getCantidadVertices() {
        return cantidadVertices;
    }

    public int getCantidadAristas() {
        return cantidadAristas;
    }

    /** Copia de los nombres de las ubicaciones (para mostrarlos en la interfaz). */
    public String[] getNombres() {
        String[] copia = new String[cantidadVertices];
        for (int i = 0; i < cantidadVertices; i++) {
            copia[i] = nombres[i];
        }
        return copia;
    }

    // ------------------------------------------------------------------
    // Aristas (rutas)
    // ------------------------------------------------------------------

    /**
     * Agrega una ruta bidireccional. Crea los vértices si no existen.
     *
     * @throws GrafoException si el tiempo no es positivo, si origen == destino
     *                        o si ya existe una ruta entre esas dos ubicaciones.
     */
    public void agregarArista(String origen, String destino, int tiempo, String idRuta) {
        if (tiempo <= 0) {
            throw new GrafoException("La ruta " + textoRuta(idRuta, origen, destino)
                    + " tiene un tiempo inválido (" + tiempo + "). Debe ser mayor que 0.");
        }
        if (normalizar(origen).equals(normalizar(destino))) {
            throw new GrafoException("La ruta " + textoRuta(idRuta, origen, destino)
                    + " tiene el mismo origen y destino.");
        }
        int i = agregarVertice(origen);
        int j = agregarVertice(destino);

        if (matriz[i][j] != SIN_CONEXION) {
            throw new GrafoException("Ruta duplicada: " + textoRuta(idRuta, origen, destino)
                    + " ya existe como " + idsRuta[i][j] + ".");
        }
        // No dirigido: se guarda en ambos sentidos
        matriz[i][j] = tiempo;
        matriz[j][i] = tiempo;
        idsRuta[i][j] = idRuta;
        idsRuta[j][i] = idRuta;
        cantidadAristas++;
    }

    public void agregarArista(String origen, String destino, int tiempo) {
        agregarArista(origen, destino, tiempo, null);
    }

    /** Tiempo directo entre i y j, o SIN_CONEXION si no hay ruta directa. */
    public int getPeso(int i, int j) {
        validarIndice(i);
        validarIndice(j);
        return matriz[i][j];
    }

    public int getPeso(String origen, String destino) {
        return getPeso(indiceObligatorio(origen), indiceObligatorio(destino));
    }

    public boolean sonAdyacentes(int i, int j) {
        return i != j && getPeso(i, j) != SIN_CONEXION;
    }

    public String getIdRuta(int i, int j) {
        validarIndice(i);
        validarIndice(j);
        return idsRuta[i][j];
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Matriz de adyacencia en texto, útil para depurar y para la defensa. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s", ""));
        for (int j = 0; j < cantidadVertices; j++) {
            sb.append(String.format(" %5.5s", nombres[j]));
        }
        sb.append('\n');
        for (int i = 0; i < cantidadVertices; i++) {
            sb.append(String.format("%-12.12s", nombres[i]));
            for (int j = 0; j < cantidadVertices; j++) {
                int peso = matriz[i][j];
                sb.append(String.format(" %5s", peso == SIN_CONEXION ? "-" : String.valueOf(peso)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Convierte "  San  José " en "san jose": minúsculas, sin tildes y sin espacios extra.
     * Así "Limon", "limón" y "LIMÓN" se consideran la misma ubicación.
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private int buscarPorClave(String clave) {
        // Búsqueda lineal: con ~16 ubicaciones es más que suficiente
        for (int i = 0; i < cantidadVertices; i++) {
            if (claves[i].equals(clave)) {
                return i;
            }
        }
        return -1;
    }

    private void validarIndice(int indice) {
        if (indice < 0 || indice >= cantidadVertices) {
            throw new GrafoException("Índice de ubicación fuera de rango: " + indice);
        }
    }

    /** Duplica la capacidad copiando a arreglos nuevos (sin usar colecciones). */
    private void crecer() {
        int nuevaCapacidad = nombres.length * 2;

        String[] nuevosNombres = new String[nuevaCapacidad];
        String[] nuevasClaves = new String[nuevaCapacidad];
        int[][] nuevaMatriz = crearMatriz(nuevaCapacidad);
        String[][] nuevosIds = new String[nuevaCapacidad][nuevaCapacidad];

        for (int i = 0; i < cantidadVertices; i++) {
            nuevosNombres[i] = nombres[i];
            nuevasClaves[i] = claves[i];
            for (int j = 0; j < cantidadVertices; j++) {
                nuevaMatriz[i][j] = matriz[i][j];
                nuevosIds[i][j] = idsRuta[i][j];
            }
        }
        nombres = nuevosNombres;
        claves = nuevasClaves;
        matriz = nuevaMatriz;
        idsRuta = nuevosIds;
    }

    private static int[][] crearMatriz(int tamano) {
        int[][] m = new int[tamano][tamano];
        for (int i = 0; i < tamano; i++) {
            for (int j = 0; j < tamano; j++) {
                m[i][j] = (i == j) ? 0 : SIN_CONEXION;
            }
        }
        return m;
    }

    private static String textoRuta(String id, String origen, String destino) {
        String base = origen + " - " + destino;
        return id == null ? base : id + " (" + base + ")";
    }
}
