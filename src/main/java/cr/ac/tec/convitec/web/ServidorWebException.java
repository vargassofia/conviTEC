package cr.ac.tec.convitec.web;

/**
 * Error al iniciar el servidor web de una sede (por ejemplo, el puerto HTTP ya está en uso).
 */
public class ServidorWebException extends RuntimeException {

    public ServidorWebException(String mensaje) {
        super(mensaje);
    }

    public ServidorWebException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
