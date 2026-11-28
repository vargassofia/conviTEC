package cr.ac.tec.convitec.acceso;

/**
 * Error de acceso, con un mensaje ya escrito para la persona que usa la página.
 * La web lo convierte en el código HTTP según el motivo (ver web/ManejadorApi).
 */
public class AccesoException extends RuntimeException {

    /** Por qué se negó el acceso. */
    public enum Motivo {
        /** No hay sesión, o terminó por inactividad (HTTP 401). */
        SIN_SESION,
        /** Usuario o contraseña incorrectos, o cuenta desactivada (HTTP 401). */
        CREDENCIALES,
        /** Demasiados intentos fallidos seguidos: hay que esperar (HTTP 429). */
        BLOQUEADO,
        /** El código de acceso no es válido, ya se usó o venció (HTTP 403). */
        CODIGO,
        /** La contraseña de quien hace el cambio no es correcta (HTTP 403). */
        CONFIRMACION
    }

    private final Motivo motivo;
    private final long segundosEspera;

    public AccesoException(Motivo motivo, String mensaje) {
        this(motivo, mensaje, 0);
    }

    public AccesoException(Motivo motivo, String mensaje, long segundosEspera) {
        super(mensaje);
        this.motivo = motivo;
        this.segundosEspera = segundosEspera;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    /** Segundos que hay que esperar antes de volver a intentar (solo para BLOQUEADO). */
    public long getSegundosEspera() {
        return segundosEspera;
    }
}
