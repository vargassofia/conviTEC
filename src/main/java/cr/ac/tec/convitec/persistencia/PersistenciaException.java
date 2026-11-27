package cr.ac.tec.convitec.persistencia;

/** Error de lectura, escritura o validación de datos persistidos. */
public class PersistenciaException extends RuntimeException {

    public PersistenciaException(String mensaje) {
        super(mensaje);
    }

    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
