package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Cualquier ruta /api/... que no exista responde 404 en JSON (y no con una página HTML).
 */
final class ApiNoEncontrada implements HttpHandler {

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            RespuestaHttp.error(intercambio, 404, "No existe el recurso " + intercambio.getRequestURI().getPath() + ".");
        } finally {
            intercambio.close();
        }
    }
}
