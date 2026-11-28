package cr.ac.tec.convitec.acceso;

import java.time.Instant;

/**
 * Una sesión abierta en la web. Vive solo en memoria: si la sede se reinicia, hay que volver a entrar.
 *
 * El token (lo que guarda la cookie del navegador) no se guarda aquí: el servicio lo busca por su SHA-256.
 */
public final class Sesion {

    private final String usuario;
    private final String nombre;
    private final Instant inicio;
    private Instant ultimoUso;

    Sesion(String usuario, String nombre, Instant inicio) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.inicio = inicio;
        this.ultimoUso = inicio;
    }

    /** Usuario de la cuenta (en minúsculas). */
    public String getUsuario() {
        return usuario;
    }

    /** Nombre de la persona, para mostrarlo en el encabezado. */
    public String getNombre() {
        return nombre;
    }

    Instant getInicio() {
        return inicio;
    }

    Instant getUltimoUso() {
        return ultimoUso;
    }

    void renovar(Instant ahora) {
        ultimoUso = ahora;
    }
}
