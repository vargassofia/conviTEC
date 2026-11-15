package cr.ac.tec.convitec.mensajeria;

/**
 * Resultado de pedirle una reserva a otra sede. Es lo que recibe la Persona 2.
 *
 * Ejemplos:
 *   exito = true,  motivo = null
 *   exito = false, motivo = "Limón: stock insuficiente de B01 (hay 1, se piden 2)"
 *   exito = false, motivo = "Limón no respondió a tiempo"
 *
 * sede = código de la sede que respondió (o a la que se le preguntó), por ejemplo "LIM".
 * El inventario local puede dejarla en null: la mensajería la completa.
 */
public final class RespuestaReserva {

    private final boolean exito;
    private final String motivo;
    private final String sede;

    public RespuestaReserva(boolean exito, String motivo, String sede) {
        this.exito = exito;
        this.motivo = motivo;
        this.sede = sede;
    }

    public static RespuestaReserva ok(String sede) {
        return new RespuestaReserva(true, null, sede);
    }

    public static RespuestaReserva rechazo(String sede, String motivo) {
        return new RespuestaReserva(false, motivo, sede);
    }

    public boolean isExito() {
        return exito;
    }

    /** Explicación del rechazo (null si fue exitosa). */
    public String getMotivo() {
        return motivo;
    }

    public String getSede() {
        return sede;
    }

    @Override
    public String toString() {
        return exito ? "Reserva OK (" + sede + ")" : "Reserva rechazada (" + sede + "): " + motivo;
    }
}
