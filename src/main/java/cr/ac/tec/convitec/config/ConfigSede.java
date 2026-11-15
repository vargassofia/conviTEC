package cr.ac.tec.convitec.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración de UNA sede, cargada desde un archivo .properties (por ejemplo sedes/sede-ALA.properties).
 *
 * Claves del archivo (todas obligatorias):
 *   sede.codigo         código de 3 letras (LIB, ALA, SJO, LIM)
 *   sede.nombre         nombre de la sede, igual al nodo del grafo ("Alajuela")
 *   http.puerto         puerto de la interfaz web / API REST
 *   rabbit.host         host del broker RabbitMQ (IP de la computadora con el broker)
 *   rabbit.puerto       puerto del broker (normalmente 5672)
 *   rabbit.usuario      usuario de RabbitMQ
 *   rabbit.clave        clave de RabbitMQ (se puede reemplazar con la variable de entorno CONVITEC_RABBIT_CLAVE)
 *   reserva.timeoutMs   milisegundos que se espera la respuesta de una reserva
 *   datos.carpeta       carpeta donde la sede guarda sus archivos
 *
 * El archivo se lee con un lector propio en UTF-8 (líneas "clave=valor", comentarios con # o !).
 * No se usa java.util.Properties porque es una colección (Hashtable) y además lee en ISO-8859-1,
 * lo que dañaría las tildes de "San José" y "Limón".
 *
 * Es inmutable.
 */
public final class ConfigSede {

    /** Variable de entorno que, si existe, reemplaza rabbit.clave (para no subir la clave real al repositorio). */
    public static final String VARIABLE_CLAVE = "CONVITEC_RABBIT_CLAVE";

    private final String codigo;
    private final String nombre;
    private final int puertoHttp;
    private final String rabbitHost;
    private final int rabbitPuerto;
    private final String rabbitUsuario;
    private final String rabbitClave;
    private final int timeoutReservaMs;
    private final String carpetaDatos;

    /**
     * @throws ConfigException si algún dato es inválido.
     */
    public ConfigSede(String codigo, String nombre, int puertoHttp, String rabbitHost, int rabbitPuerto,
                      String rabbitUsuario, String rabbitClave, int timeoutReservaMs, String carpetaDatos) {
        if (codigo == null || !codigo.trim().matches("[A-Za-z]{3}")) {
            throw new ConfigException("El código de sede debe tener exactamente 3 letras (llegó '" + codigo + "').");
        }
        exigirTexto(nombre, "sede.nombre");
        exigirTexto(rabbitHost, "rabbit.host");
        exigirTexto(rabbitUsuario, "rabbit.usuario");
        exigirTexto(carpetaDatos, "datos.carpeta");
        if (rabbitClave == null) {
            throw new ConfigException("Falta 'rabbit.clave' en la configuración.");
        }
        validarPuerto(puertoHttp, "http.puerto");
        validarPuerto(rabbitPuerto, "rabbit.puerto");
        if (timeoutReservaMs <= 0) {
            throw new ConfigException("'reserva.timeoutMs' debe ser mayor que 0 (llegó " + timeoutReservaMs + ").");
        }
        this.codigo = codigo.trim().toUpperCase();
        this.nombre = nombre.trim();
        this.puertoHttp = puertoHttp;
        this.rabbitHost = rabbitHost.trim();
        this.rabbitPuerto = rabbitPuerto;
        this.rabbitUsuario = rabbitUsuario.trim();
        this.rabbitClave = rabbitClave;
        this.timeoutReservaMs = timeoutReservaMs;
        this.carpetaDatos = carpetaDatos.trim();
    }

    // ------------------------------------------------------------------
    // Carga
    // ------------------------------------------------------------------

    /** Carga desde un archivo en disco. */
    public static ConfigSede cargarDesdeArchivo(Path archivo) {
        if (archivo == null || !Files.exists(archivo)) {
            throw new ConfigException("No se encontró el archivo de configuración: " + archivo);
        }
        try {
            return desdeTexto(Files.readString(archivo, StandardCharsets.UTF_8), archivo.toString());
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer el archivo de configuración: " + archivo, e);
        }
    }

    /** Carga desde src/main/resources, por ejemplo "sedes/sede-ALA.properties". */
    public static ConfigSede cargarDesdeRecurso(String nombreRecurso) {
        try (InputStream entrada = ConfigSede.class.getClassLoader().getResourceAsStream(nombreRecurso)) {
            if (entrada == null) {
                throw new ConfigException("No se encontró el recurso de configuración: " + nombreRecurso);
            }
            return desdeTexto(new String(entrada.readAllBytes(), StandardCharsets.UTF_8), nombreRecurso);
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer el recurso de configuración: " + nombreRecurso, e);
        }
    }

    /**
     * Carga desde el texto de un .properties. La clave de RabbitMQ se reemplaza por la
     * variable de entorno CONVITEC_RABBIT_CLAVE si existe.
     *
     * @param origen nombre del archivo, solo para los mensajes de error
     */
    public static ConfigSede desdeTexto(String texto, String origen) {
        return desdeTexto(texto, origen, System.getenv(VARIABLE_CLAVE));
    }

    /** Igual que desdeTexto(texto, origen), pero recibe la clave del entorno (para poder probarlo). */
    static ConfigSede desdeTexto(String texto, String origen, String claveEntorno) {
        if (texto == null || texto.isBlank()) {
            throw new ConfigException("El archivo de configuración '" + origen + "' está vacío.");
        }
        String[] lineas = texto.split("\\R");
        String[] claves = new String[lineas.length];
        String[] valores = new String[lineas.length];
        int cantidad = 0;

        for (int n = 0; n < lineas.length; n++) {
            String linea = lineas[n].trim();
            if (linea.isEmpty() || linea.startsWith("#") || linea.startsWith("!")) {
                continue;
            }
            int separador = linea.indexOf('=');
            if (separador <= 0) {
                throw new ConfigException(origen + ", línea " + (n + 1) + ": se esperaba 'clave=valor' y vino '"
                        + linea + "'.");
            }
            String clave = linea.substring(0, separador).trim();
            String valor = linea.substring(separador + 1).trim();
            if (buscar(claves, cantidad, clave) != -1) {
                throw new ConfigException(origen + ": la clave '" + clave + "' está repetida.");
            }
            claves[cantidad] = clave;
            valores[cantidad] = valor;
            cantidad++;
        }

        String clave = obligatorio(claves, valores, cantidad, "rabbit.clave", origen);
        if (claveEntorno != null && !claveEntorno.isEmpty()) {
            clave = claveEntorno;
        }
        return new ConfigSede(
                obligatorio(claves, valores, cantidad, "sede.codigo", origen),
                obligatorio(claves, valores, cantidad, "sede.nombre", origen),
                numero(claves, valores, cantidad, "http.puerto", origen),
                obligatorio(claves, valores, cantidad, "rabbit.host", origen),
                numero(claves, valores, cantidad, "rabbit.puerto", origen),
                obligatorio(claves, valores, cantidad, "rabbit.usuario", origen),
                clave,
                numero(claves, valores, cantidad, "reserva.timeoutMs", origen),
                obligatorio(claves, valores, cantidad, "datos.carpeta", origen));
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPuertoHttp() {
        return puertoHttp;
    }

    public String getRabbitHost() {
        return rabbitHost;
    }

    public int getRabbitPuerto() {
        return rabbitPuerto;
    }

    public String getRabbitUsuario() {
        return rabbitUsuario;
    }

    public String getRabbitClave() {
        return rabbitClave;
    }

    public int getTimeoutReservaMs() {
        return timeoutReservaMs;
    }

    public String getCarpetaDatos() {
        return carpetaDatos;
    }

    /** No muestra la clave. */
    @Override
    public String toString() {
        return "Sede " + codigo + " (" + nombre + ") | http " + puertoHttp + " | rabbit "
                + rabbitUsuario + "@" + rabbitHost + ":" + rabbitPuerto + " | timeout " + timeoutReservaMs
                + " ms | datos " + carpetaDatos;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static int buscar(String[] claves, int cantidad, String clave) {
        for (int i = 0; i < cantidad; i++) {
            if (claves[i].equals(clave)) {
                return i;
            }
        }
        return -1;
    }

    private static String obligatorio(String[] claves, String[] valores, int cantidad, String clave, String origen) {
        int i = buscar(claves, cantidad, clave);
        if (i == -1 || valores[i].isEmpty()) {
            throw new ConfigException(origen + ": falta el campo obligatorio '" + clave + "'.");
        }
        return valores[i];
    }

    private static int numero(String[] claves, String[] valores, int cantidad, String clave, String origen) {
        String texto = obligatorio(claves, valores, cantidad, clave, origen);
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new ConfigException(origen + ": '" + clave + "' debe ser un número entero (vino '" + texto + "').");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ConfigException("Falta '" + campo + "' en la configuración.");
        }
    }

    private static void validarPuerto(int puerto, String campo) {
        if (puerto < 1 || puerto > 65535) {
            throw new ConfigException("'" + campo + "' debe estar entre 1 y 65535 (llegó " + puerto + ").");
        }
    }
}
