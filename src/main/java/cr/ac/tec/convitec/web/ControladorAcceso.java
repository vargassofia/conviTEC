package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpExchange;
import cr.ac.tec.convitec.acceso.AccesoException;
import cr.ac.tec.convitec.acceso.ServicioAcceso;
import cr.ac.tec.convitec.acceso.Sesion;
import cr.ac.tec.convitec.config.ConfigException;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.config.SedesConocidas;

import java.io.IOException;

/**
 * Inicio de sesión y sección Cuentas.
 *
 *   GET  /api/acceso                  (público) sede, las 4 sedes con su puerto, si falta la primera cuenta y la sesión
 *   POST /api/acceso/entrar           (público) { usuario, clave }
 *   POST /api/acceso/salir            (público) cierra la sesión
 *   POST /api/acceso/codigo           (público) { codigo, nombre, usuario, clave }: primera cuenta o recuperar acceso
 *   GET  /api/cuentas                 cuentas de la sede
 *   POST /api/cuentas                 { nombre, usuario, clave, claveConfirmacion }
 *   POST /api/cuentas/estado          { usuario, activa, claveConfirmacion }
 *   POST /api/cuentas/clave           { usuario, claveNueva, claveConfirmacion }
 *
 * "claveConfirmacion" es la contraseña de quien hace el cambio.
 */
final class ControladorAcceso {

    private final ServicioAcceso acceso;
    private final String nombreCookie;
    private final SedeDTO sede;
    private final SedeDTO[] sedes;

    ControladorAcceso(ServicioAcceso acceso, ConfigSede config) {
        this.acceso = acceso;
        this.nombreCookie = Cookies.nombrePara(config.getCodigo());
        this.sede = new SedeDTO(config.getCodigo(), config.getNombre(), config.getPuertoHttp(), true);
        this.sedes = sedesConocidas(config);
    }

    String getNombreCookie() {
        return nombreCookie;
    }

    // ------------------------------------------------------------------
    // Público
    // ------------------------------------------------------------------

    Object estado(HttpExchange x) {
        Sesion sesion = FiltroSesion.sesionDe(x, acceso, nombreCookie);
        return new EstadoDTO(sede, sedes, acceso.necesitaInstalacion(), ServicioAcceso.MINUTOS_INACTIVIDAD,
                sesion == null ? null : new UsuarioDTO(sesion.getUsuario(), sesion.getNombre()));
    }

    Object entrar(HttpExchange x) throws IOException {
        SolicitudEntrar s = ManejadorApi.leerJson(x, SolicitudEntrar.class);
        String token = acceso.entrar(s.usuario, s.clave);
        return conSesion(x, token);
    }

    Object salir(HttpExchange x) {
        acceso.salir(Cookies.leer(x, nombreCookie));
        Cookies.borrar(x, nombreCookie);
        return new Mensaje("Sesión cerrada.");
    }

    Object usarCodigo(HttpExchange x) throws IOException {
        SolicitudCodigo s = ManejadorApi.leerJson(x, SolicitudCodigo.class);
        String token = acceso.usarCodigoAcceso(s.codigo, s.nombre, s.usuario, s.clave);
        return conSesion(x, token);
    }

    // ------------------------------------------------------------------
    // Sección Cuentas (con sesión)
    // ------------------------------------------------------------------

    Object cuentas(HttpExchange x) throws IOException {
        Sesion quien = sesion(x);
        if ("POST".equalsIgnoreCase(x.getRequestMethod())) {
            SolicitudCuenta s = ManejadorApi.leerJson(x, SolicitudCuenta.class);
            acceso.crearCuenta(quien, s.claveConfirmacion, s.nombre, s.usuario, s.clave);
            return new Mensaje("Se creó la cuenta de " + s.nombre.trim() + ".");
        }
        ServicioAcceso.Cuenta[] todas = acceso.cuentas();
        CuentaDTO[] resultado = new CuentaDTO[todas.length];
        for (int i = 0; i < todas.length; i++) {
            ServicioAcceso.Cuenta c = todas[i];
            resultado[i] = new CuentaDTO(c.usuario, c.nombre, c.activa, c.creadaEn, c.creadaPor, c.ultimoIngreso,
                    c.usuario.equals(quien.getUsuario()));
        }
        return resultado;
    }

    Object cambiarEstado(HttpExchange x) throws IOException {
        Sesion quien = sesion(x);
        SolicitudEstado s = ManejadorApi.leerJson(x, SolicitudEstado.class);
        if (s.activa == null) {
            throw new IllegalArgumentException("Indique si la cuenta queda activa o no.");
        }
        acceso.cambiarEstado(quien, s.claveConfirmacion, s.usuario, s.activa);
        return new Mensaje(s.activa ? "La cuenta quedó activa." : "La cuenta quedó desactivada.");
    }

    Object cambiarClave(HttpExchange x) throws IOException {
        Sesion quien = sesion(x);
        SolicitudClave s = ManejadorApi.leerJson(x, SolicitudClave.class);
        acceso.cambiarClave(quien, s.claveConfirmacion, s.usuario, s.claveNueva);
        return new Mensaje("Se cambió la contraseña.");
    }

    // ------------------------------------------------------------------

    private Object conSesion(HttpExchange x, String token) {
        Cookies.escribir(x, nombreCookie, token);
        Sesion sesion = acceso.validar(token, false);
        return new UsuarioDTO(sesion.getUsuario(), sesion.getNombre());
    }

    private static Sesion sesion(HttpExchange x) {
        Object valor = x.getAttribute(FiltroSesion.ATRIBUTO_SESION);
        if (!(valor instanceof Sesion)) {
            throw new AccesoException(AccesoException.Motivo.SIN_SESION, "Inicie sesión para continuar.");
        }
        return (Sesion) valor;
    }

    /** Las 4 sedes con el puerto de su archivo de configuración; esta sede con el puerto real. */
    private SedeDTO[] sedesConocidas(ConfigSede config) {
        String[] codigos = SedesConocidas.codigos();
        SedeDTO[] resultado = new SedeDTO[codigos.length];
        for (int i = 0; i < codigos.length; i++) {
            if (codigos[i].equals(config.getCodigo())) {
                resultado[i] = sede;
                continue;
            }
            int puerto = 0;
            try {
                puerto = ConfigSede.cargarDesdeRecurso("sedes/sede-" + codigos[i] + ".properties").getPuertoHttp();
            } catch (ConfigException | IllegalArgumentException e) {
                // Sin puerto conocido la página muestra la sede, pero sin enlace
            }
            resultado[i] = new SedeDTO(codigos[i], SedesConocidas.nombreDe(codigos[i]), puerto, false);
        }
        return resultado;
    }

    // ------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------

    static final class SolicitudEntrar {
        String usuario;
        String clave;
    }

    static final class SolicitudCodigo {
        String codigo;
        String nombre;
        String usuario;
        String clave;
    }

    static final class SolicitudCuenta {
        String nombre;
        String usuario;
        String clave;
        String claveConfirmacion;
    }

    static final class SolicitudEstado {
        String usuario;
        Boolean activa;
        String claveConfirmacion;
    }

    static final class SolicitudClave {
        String usuario;
        String claveNueva;
        String claveConfirmacion;
    }

    static final class EstadoDTO {
        final SedeDTO sede;
        final SedeDTO[] sedes;
        final boolean necesitaInstalacion;
        final int minutosInactividad;
        final UsuarioDTO sesion;

        EstadoDTO(SedeDTO sede, SedeDTO[] sedes, boolean necesitaInstalacion, int minutosInactividad,
                  UsuarioDTO sesion) {
            this.sede = sede;
            this.sedes = sedes;
            this.necesitaInstalacion = necesitaInstalacion;
            this.minutosInactividad = minutosInactividad;
            this.sesion = sesion;
        }
    }

    static final class SedeDTO {
        final String codigo;
        final String nombre;
        final int puerto;
        final boolean esEstaSede;

        SedeDTO(String codigo, String nombre, int puerto, boolean esEstaSede) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.puerto = puerto;
            this.esEstaSede = esEstaSede;
        }
    }

    static final class UsuarioDTO {
        final String usuario;
        final String nombre;

        UsuarioDTO(String usuario, String nombre) {
            this.usuario = usuario;
            this.nombre = nombre;
        }
    }

    static final class CuentaDTO {
        final String usuario;
        final String nombre;
        final boolean activa;
        final String creadaEn;
        final String creadaPor;
        final String ultimoIngreso;
        final boolean esUsted;

        CuentaDTO(String usuario, String nombre, boolean activa, String creadaEn, String creadaPor,
                  String ultimoIngreso, boolean esUsted) {
            this.usuario = usuario;
            this.nombre = nombre;
            this.activa = activa;
            this.creadaEn = creadaEn;
            this.creadaPor = creadaPor;
            this.ultimoIngreso = ultimoIngreso;
            this.esUsted = esUsted;
        }
    }

    static final class Mensaje {
        final String mensaje;

        Mensaje(String mensaje) {
            this.mensaje = mensaje;
        }
    }
}
