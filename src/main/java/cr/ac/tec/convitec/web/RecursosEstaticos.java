package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sirve los archivos de la interfaz web desde src/main/resources/web (dentro del jar).
 *
 *   /                    -> web/index.html
 *   /css/estilos.css     -> web/css/estilos.css
 *
 * Seguridad:
 * - Solo GET y HEAD.
 * - Se rechazan rutas con "..", "\" o "//", para que nadie pida archivos fuera de la carpeta web.
 * - Solo se sirven extensiones conocidas (html, css, js, json, svg, png, ico, woff2). Así tampoco se
 *   devuelve el listado de una carpeta.
 */
final class RecursosEstaticos implements HttpHandler {

    private static final String[] EXTENSIONES = {".html", ".css", ".js", ".json", ".svg", ".png", ".ico", ".woff2"};
    private static final String[] TIPOS = {
            "text/html; charset=utf-8",
            "text/css; charset=utf-8",
            "text/javascript; charset=utf-8",
            "application/json; charset=utf-8",
            "image/svg+xml",
            "image/png",
            "image/x-icon",
            "font/woff2"
    };

    private final String carpeta;

    /** @param carpeta carpeta dentro de los recursos, por ejemplo "web". */
    RecursosEstaticos(String carpeta) {
        this.carpeta = carpeta;
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            String metodo = intercambio.getRequestMethod();
            if (!"GET".equalsIgnoreCase(metodo) && !"HEAD".equalsIgnoreCase(metodo)) {
                intercambio.getResponseHeaders().set("Allow", "GET, HEAD");
                noEncontrado(intercambio, 405, "Método no permitido.");
                return;
            }
            String camino = intercambio.getRequestURI().getPath();
            if (camino == null || camino.isEmpty() || "/".equals(camino)) {
                camino = "/index.html";
            }
            String tipo = tipoDe(camino);
            if (!esSeguro(camino) || tipo == null) {
                noEncontrado(intercambio, 404, "No se encontró la página " + camino + ".");
                return;
            }
            byte[] contenido = leerRecurso(carpeta + camino);
            if (contenido == null) {
                noEncontrado(intercambio, 404, "No se encontró la página " + camino + ".");
                return;
            }
            RespuestaHttp.enviar(intercambio, 200, tipo, contenido);
        } finally {
            intercambio.close();
        }
    }

    /** Tipo de contenido según la extensión, o null si no es una extensión permitida. */
    static String tipoDe(String camino) {
        String minusculas = camino.toLowerCase();
        for (int i = 0; i < EXTENSIONES.length; i++) {
            if (minusculas.endsWith(EXTENSIONES[i])) {
                return TIPOS[i];
            }
        }
        return null;
    }

    static boolean esSeguro(String camino) {
        return camino.startsWith("/") && !camino.contains("..") && !camino.contains("\\") && !camino.contains("//");
    }

    private byte[] leerRecurso(String nombre) throws IOException {
        try (InputStream entrada = RecursosEstaticos.class.getClassLoader().getResourceAsStream(nombre)) {
            return entrada == null ? null : entrada.readAllBytes();
        }
    }

    private static void noEncontrado(HttpExchange intercambio, int estado, String mensaje) throws IOException {
        RespuestaHttp.enviar(intercambio, estado, "text/plain; charset=utf-8", mensaje.getBytes(StandardCharsets.UTF_8));
    }
}
