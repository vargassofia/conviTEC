package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.MensajeriaException;

import java.io.IOException;

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

    private boolean aceptaMetodo(String metodo) {
        for (int i = 0; i < metodos.length; i++) {
            if (metodos[i].equalsIgnoreCase(metodo)) {
                return true;
            }
        }
        return false;
    }
}
