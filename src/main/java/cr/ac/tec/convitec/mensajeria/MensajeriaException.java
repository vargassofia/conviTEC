package cr.ac.tec.convitec.mensajeria;

/**
 * Error del módulo de mensajería: broker caído, mensaje mal formado, mensajería sin iniciar, etc.
 */
public class MensajeriaException extends RuntimeException {

    public MensajeriaException(String mensaje) {
        super(mensaje);
    }

    public MensajeriaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
