package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import cr.ac.tec.convitec.acceso.ServicioAcceso;
import cr.ac.tec.convitec.acceso.Sesion;

import java.io.IOException;

/**
 * Se pone delante de cada endpoint de la API (ver ServidorWeb).
 *
 * 1. Toda petición que cambia algo (POST) debe traer el encabezado "X-ConviTEC: 1", que la página agrega
 *    siempre (js/api.js). Otra página web no puede agregarlo sin permiso del servidor (CORS), así que esto
 *    impide que un sitio ajeno haga pedidos a nombre de quien tiene la sesión abierta (CSRF). -> 403
 * 2. Si el endpoint es protegido, exige una sesión válida en la cookie de la sede. -> 401
 *    La sesión queda en el atributo {@link #ATRIBUTO_SESION} para el controlador.
 *
 * Las actualizaciones automáticas de la página (cada 30 s) mandan "X-ConviTEC-Automatica: 1": esas no
 * cuentan como actividad, para que la sesión se cierre a los 30 minutos aunque la página quede abierta.
 */
final class FiltroSesion extends Filter {

    static final String ATRIBUTO_SESION = "convitec.sesion";
    static final String ENCABEZADO_APP = "X-ConviTEC";
    static final String ENCABEZADO_AUTOMATICA = "X-ConviTEC-Automatica";

    private final ServicioAcceso acceso;
    private final String nombreCookie;
    private final boolean exigeSesion;

    FiltroSesion(ServicioAcceso acceso, String nombreCookie, boolean exigeSesion) {
        this.acceso = acceso;
        this.nombreCookie = nombreCookie;
        this.exigeSesion = exigeSesion;
    }

    @Override
    public void doFilter(HttpExchange intercambio, Chain cadena) throws IOException {
        String metodo = intercambio.getRequestMethod();
        boolean soloLectura = "GET".equalsIgnoreCase(metodo) || "HEAD".equalsIgnoreCase(metodo);
        if (!soloLectura && !"1".equals(intercambio.getRequestHeaders().getFirst(ENCABEZADO_APP))) {
            responderError(intercambio, 403, "La petición no viene de la página de ConviTEC.");
            return;
        }
        if (exigeSesion) {
            Sesion sesion = sesionDe(intercambio, acceso, nombreCookie);
            if (sesion == null) {
                Cookies.borrar(intercambio, nombreCookie);
                responderError(intercambio, 401, "Su sesión terminó. Vuelva a iniciar sesión para continuar.");
                return;
            }
            intercambio.setAttribute(ATRIBUTO_SESION, sesion);
        }
        cadena.doFilter(intercambio);
    }

    /** La sesión de la cookie (o null). Renueva el tiempo salvo en las actualizaciones automáticas. */
    static Sesion sesionDe(HttpExchange intercambio, ServicioAcceso acceso, String nombreCookie) {
        boolean automatica = "1".equals(intercambio.getRequestHeaders().getFirst(ENCABEZADO_AUTOMATICA));
        return acceso.validar(Cookies.leer(intercambio, nombreCookie), !automatica);
    }

    @Override
    public String description() {
        return exigeSesion ? "Exige sesión iniciada" : "Revisa el origen de la petición";
    }

    private static void responderError(HttpExchange intercambio, int estado, String mensaje) throws IOException {
        try {
            RespuestaHttp.error(intercambio, estado, mensaje);
        } finally {
            intercambio.close();
        }
    }
}
