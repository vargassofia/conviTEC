package cr.ac.tec.convitec.config;

/**
 * Error en la configuración de una sede: archivo inexistente, campo faltante,
 * número inválido, clave repetida, etc.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String mensaje) {
        super(mensaje);
    }

    public ConfigException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
