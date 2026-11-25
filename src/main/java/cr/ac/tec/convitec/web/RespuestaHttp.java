package cr.ac.tec.convitec.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utilidades para escribir respuestas HTTP (JSON, errores y archivos).
 *
 * Todos los errores de la API tienen la misma forma, para que la interfaz los muestre igual:
 *   { "estado": 404, "error": "La ubicación 'Narnia' no existe en el mapa de rutas." }
 */
final class RespuestaHttp {

    static final String TIPO_JSON = "application/json; charset=utf-8";

    /** disableHtmlEscaping: así "San José" o "->" viajan tal cual y no como é o >. */
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RespuestaHttp() {
        // Clase de utilidad: no se instancia
    }

    /** Envía un objeto como JSON. */
    static void json(HttpExchange intercambio, int estado, Object cuerpo) throws IOException {
        enviar(intercambio, estado, TIPO_JSON, GSON.toJson(cuerpo).getBytes(StandardCharsets.UTF_8));
    }

    /** Envía un error en JSON: { "estado": ..., "error": "..." }. */
    static void error(HttpExchange intercambio, int estado, String mensaje) throws IOException {
        json(intercambio, estado, new ErrorDTO(estado, mensaje));
    }

    /**
     * Envía la respuesta completa. En una petición HEAD solo se mandan los encabezados.
     */
    static void enviar(HttpExchange intercambio, int estado, String tipoContenido, byte[] cuerpo) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipoContenido);
        intercambio.getResponseHeaders().set("Cache-Control", "no-cache");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        boolean sinCuerpo = "HEAD".equalsIgnoreCase(intercambio.getRequestMethod()) || cuerpo.length == 0;
        if (sinCuerpo) {
            intercambio.sendResponseHeaders(estado, -1); // -1 = sin cuerpo
            return;
        }
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    /** Forma de los errores de la API. Gson lo convierte a JSON. */
    static final class ErrorDTO {
        final int estado;
        final String error;

        ErrorDTO(int estado, String error) {
            this.estado = estado;
            this.error = error;
        }
    }
}
