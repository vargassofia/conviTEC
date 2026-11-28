package cr.ac.tec.convitec.web;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import cr.ac.tec.convitec.acceso.AccesoException;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.MensajeriaException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Base de todos los endpoints de la API REST.
 *
 * Cada endpoint indica su ruta EXACTA y los métodos que acepta, e implementa atender(),
 * que devuelve el objeto a enviar como JSON. Aquí se resuelve lo común:
 *
 * - Ruta distinta (ej. "/api/sedeX" llega al contexto "/api/sede") -> 404
 * - Método no permitido                                            -> 405
 * - IllegalArgumentException (dato inválido)                       -> 400
 * - GrafoException (ubicación inexistente, etc.)                   -> 422
 * - IllegalStateException (operación que no aplica ahora)          -> 409
 * - AccesoException (sesión, contraseña, código)                   -> 401, 403 o 429 (ver estadoDe)
 * - MensajeriaException (RabbitMQ caído)                           -> 503
 * - Cualquier otro error                                           -> 500, con un mensaje genérico
 *
 * Los mensajes de 400, 409, 422 y 503 vienen de las excepciones del backend y ya están escritos
 * para el usuario, así que la interfaz los muestra tal cual.
 */
abstract class ManejadorApi implements HttpHandler {

    private final String ruta;
    private final String[] metodos;

    /**
     * @param ruta    ruta exacta, por ejemplo "/api/sede"
     * @param metodos métodos aceptados, por ejemplo "GET"
     */
    protected ManejadorApi(String ruta, String... metodos) {
        this.ruta = ruta;
        this.metodos = metodos;
    }

    /** Hace el trabajo del endpoint y devuelve lo que se envía como JSON (código 200). */
    protected abstract Object atender(HttpExchange intercambio) throws IOException;

    /** Lo que hace un endpoint sencillo, escrito como lambda. */
    @FunctionalInterface
    interface Accion {
        Object atender(HttpExchange intercambio) throws IOException;
    }

    /**
     * Endpoint sin clase propia:
     *   ManejadorApi.de("/api/entregas/siguiente", x -> controlador.siguiente(), "POST")
     */
    static ManejadorApi de(String ruta, Accion accion, String... metodos) {
        return new ManejadorApi(ruta, metodos) {
            @Override
            protected Object atender(HttpExchange intercambio) throws IOException {
                return accion.atender(intercambio);
            }
        };
    }

    // ------------------------------------------------------------------
    // Lectura de la petición
    // ------------------------------------------------------------------

    /** Tamaño máximo del cuerpo de una petición (los pedidos son pequeños). */
    static final int MAXIMO_CUERPO = 64 * 1024;

    /**
     * Valor de un parámetro de la dirección (?destino=San%20José -> "San José"), o null si no viene.
     */
    static String parametro(HttpExchange intercambio, String nombre) {
        String consulta = intercambio.getRequestURI().getRawQuery();
        if (consulta == null || consulta.isEmpty()) {
            return null;
        }
        String[] pares = consulta.split("&");
        for (int i = 0; i < pares.length; i++) {
            int igual = pares[i].indexOf('=');
            String clave = igual == -1 ? pares[i] : pares[i].substring(0, igual);
            if (decodificar(clave).equals(nombre)) {
                return igual == -1 ? "" : decodificar(pares[i].substring(igual + 1));
            }
        }
        return null;
    }

    /**
     * Lee el cuerpo JSON de la petición como un objeto de la clase indicada.
     *
     * @throws IllegalArgumentException si falta el cuerpo, es demasiado grande o no es JSON válido.
     */
    static <T> T leerJson(HttpExchange intercambio, Class<T> clase) throws IOException {
        byte[] bytes = intercambio.getRequestBody().readNBytes(MAXIMO_CUERPO + 1);
        if (bytes.length > MAXIMO_CUERPO) {
            throw new IllegalArgumentException("La petición es demasiado grande.");
        }
        String texto = new String(bytes, StandardCharsets.UTF_8);
        if (texto.isBlank()) {
            throw new IllegalArgumentException("La petición no trae datos.");
        }
        T objeto;
        try {
            objeto = RespuestaHttp.GSON.fromJson(texto, clase);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Los datos enviados no tienen un formato válido.");
        }
        if (objeto == null) {
            throw new IllegalArgumentException("La petición no trae datos.");
        }
        return objeto;
    }

    private static String decodificar(String texto) {
        try {
            return URLDecoder.decode(texto, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La dirección tiene caracteres inválidos.");
        }
    }

    @Override
    public final void handle(HttpExchange intercambio) throws IOException {
        try {
            String camino = intercambio.getRequestURI().getPath();
            if (!ruta.equals(camino)) {
                RespuestaHttp.error(intercambio, 404, "No existe el recurso " + camino + ".");
                return;
            }
            if (!aceptaMetodo(intercambio.getRequestMethod())) {
                intercambio.getResponseHeaders().set("Allow", String.join(", ", metodos));
                RespuestaHttp.error(intercambio, 405, "El método " + intercambio.getRequestMethod()
                        + " no se permite en " + ruta + ".");
                return;
            }

            int estado;
            Object cuerpo;
            try {
                cuerpo = atender(intercambio);
                estado = 200;
            } catch (IllegalArgumentException e) {
                estado = 400;
                cuerpo = new RespuestaHttp.ErrorDTO(estado, e.getMessage());
            } catch (GrafoException e) {
                estado = 422;
                cuerpo = new RespuestaHttp.ErrorDTO(estado, e.getMessage());
            } catch (IllegalStateException e) {
                estado = 409;
                cuerpo = new RespuestaHttp.ErrorDTO(estado, e.getMessage());
            } catch (AccesoException e) {
                estado = estadoDe(e);
                if (e.getMotivo() == AccesoException.Motivo.BLOQUEADO) {
                    intercambio.getResponseHeaders().set("Retry-After", String.valueOf(e.getSegundosEspera()));
                }
                cuerpo = new RespuestaHttp.ErrorDTO(estado, e.getMessage());
            } catch (MensajeriaException e) {
                estado = 503;
                cuerpo = new RespuestaHttp.ErrorDTO(estado, "No hay comunicación con las otras sedes: " + e.getMessage());
            } catch (RuntimeException e) {
                System.err.println("[ConviTEC web] Error inesperado en " + ruta + ": " + e);
                estado = 500;
                cuerpo = new RespuestaHttp.ErrorDTO(estado, "Ocurrió un error inesperado en el servidor de la sede.");
            }
            RespuestaHttp.json(intercambio, estado, cuerpo);
        } finally {
            intercambio.close();
        }
    }

    /** SIN_SESION y CREDENCIALES -> 401, BLOQUEADO -> 429, CODIGO y CONFIRMACION -> 403. */
    static int estadoDe(AccesoException e) {
        switch (e.getMotivo()) {
            case BLOQUEADO:
                return 429;
            case CODIGO:
            case CONFIRMACION:
                return 403;
            default:
                return 401;
        }
    }

    private boolean aceptaMetodo(String metodo) {
        for (int i = 0; i < metodos.length; i++) {
            if (metodos[i].equalsIgnoreCase(metodo)) {
                return true;
            }
        }
        return false;
    }
}
