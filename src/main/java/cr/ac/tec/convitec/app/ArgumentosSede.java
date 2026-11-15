package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.config.SedesConocidas;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Argumentos del programa de una sede.
 *
 *   Uso: <CODIGO_SEDE> [--config ruta/al/archivo.properties]
 *
 * - Sin --config se usa el recurso interno sedes/sede-<COD>.properties (localhost).
 * - Con --config se usa un archivo externo: así, en otra computadora se cambia rabbit.host
 *   sin recompilar.
 */
public final class ArgumentosSede {

    public static final String USO = "Uso: java -jar convitec-sede.jar <CODIGO_SEDE> [--config ruta/al/archivo.properties]\n"
            + "  CODIGO_SEDE: LIB (Liberia), ALA (Alajuela), SJO (San José) o LIM (Limón)\n"
            + "  Ejemplo: java -jar target/convitec-sede.jar ALA --config mi-sede-ALA.properties";

    private static final String OPCION_CONFIG = "--config";

    private final String codigoSede;
    private final Path archivoConfig; // null = recurso interno

    private ArgumentosSede(String codigoSede, Path archivoConfig) {
        this.codigoSede = codigoSede;
        this.archivoConfig = archivoConfig;
    }

    /**
     * @throws IllegalArgumentException con el texto de uso si los argumentos están mal.
     */
    public static ArgumentosSede interpretar(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Falta el código de la sede.\n" + USO);
        }
        String codigo = args[0].trim().toUpperCase();
        if (!SedesConocidas.esCodigoValido(codigo)) {
            throw new IllegalArgumentException("Código de sede desconocido: '" + args[0] + "'.\n" + USO);
        }
        if (args.length == 1) {
            return new ArgumentosSede(codigo, null);
        }
        if (!OPCION_CONFIG.equals(args[1])) {
            throw new IllegalArgumentException("Opción desconocida: '" + args[1] + "'.\n" + USO);
        }
        if (args.length < 3 || args[2] == null || args[2].isBlank()) {
            throw new IllegalArgumentException("Falta la ruta del archivo después de --config.\n" + USO);
        }
        if (args.length > 3) {
            throw new IllegalArgumentException("Sobran argumentos después de la ruta del archivo.\n" + USO);
        }
        Path archivo = Path.of(args[2]);
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalArgumentException("No existe el archivo de configuración: " + archivo.toAbsolutePath()
                    + "\n" + USO);
        }
        return new ArgumentosSede(codigo, archivo);
    }

    /**
     * Carga la configuración (archivo externo o recurso interno) y revisa que sea de esta sede.
     *
     * @throws cr.ac.tec.convitec.config.ConfigException si el archivo tiene errores.
     * @throws IllegalArgumentException si el archivo es de otra sede.
     */
    public ConfigSede cargarConfiguracion() {
        ConfigSede config = archivoConfig == null
                ? ConfigSede.cargarDesdeRecurso(getRecursoConfig())
                : ConfigSede.cargarDesdeArchivo(archivoConfig);
        if (!config.getCodigo().equals(codigoSede)) {
            throw new IllegalArgumentException("El archivo de configuración es de la sede " + config.getCodigo()
                    + ", pero se pidió arrancar " + codigoSede + ".");
        }
        return config;
    }

    public String getCodigoSede() {
        return codigoSede;
    }

    /** Archivo externo, o null si se usa el recurso interno. */
    public Path getArchivoConfig() {
        return archivoConfig;
    }

    public String getRecursoConfig() {
        return "sedes/sede-" + codigoSede + ".properties";
    }
}
