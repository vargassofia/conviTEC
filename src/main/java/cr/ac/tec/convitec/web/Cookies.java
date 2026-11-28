package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpExchange;

/**
 * Cookie de sesión de la web.
 *
 * Su nombre incluye la sede ("convitec_SJO") porque el navegador comparte las cookies entre puertos:
 * localhost:8081 y localhost:8083 verían la misma cookie si se llamara igual.
 *
 * - HttpOnly: el JavaScript de la página no puede leerla (si alguien lograra meter código, no se la lleva).
 * - SameSite=Strict: el navegador no la envía en peticiones que vienen de otros sitios.
 * - Sin Max-Age: se borra al cerrar el navegador. El servidor la vence a los 30 minutos sin uso.
 */
final class Cookies {

    private Cookies() {
        // Clase de utilidad: no se instancia
    }

    static String nombrePara(String codigoSede) {
        return "convitec_" + codigoSede;
    }

    /** Valor de la cookie, o null si la petición no la trae. */
    static String leer(HttpExchange intercambio, String nombre) {
        String encabezado = intercambio.getRequestHeaders().getFirst("Cookie");
        if (encabezado == null) {
            return null;
        }
        String[] partes = encabezado.split(";");
        for (int i = 0; i < partes.length; i++) {
            String parte = partes[i].trim();
            int igual = parte.indexOf('=');
            if (igual > 0 && parte.substring(0, igual).equals(nombre)) {
                return parte.substring(igual + 1).trim();
            }
        }
        return null;
    }

    static void escribir(HttpExchange intercambio, String nombre, String token) {
        intercambio.getResponseHeaders().add("Set-Cookie", nombre + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
    }

    static void borrar(HttpExchange intercambio, String nombre) {
        intercambio.getResponseHeaders().add("Set-Cookie", nombre + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
    }
}
