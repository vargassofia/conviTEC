package cr.ac.tec.convitec.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inicio de sesión, cuentas y protección de la API, con peticiones HTTP reales. */
class ApiAccesoTest {

    private final HttpClient cliente = HttpClient.newHttpClient();
    private SedeWebDePrueba sede;
    private ServidorWeb servidor;

    @TempDir
    Path carpeta;

    @BeforeEach
    void iniciar() {
        sede = SedeWebDePrueba.crear("ALA", carpeta.resolve("ALA"));
        servidor = sede.servidor(0, Clock.systemDefaultZone());
        servidor.iniciar();
    }

    @AfterEach
    void cerrar() {
        servidor.detener();
    }

    @Test
    void sinSesionLaApiNoResponde() throws Exception {
        String[] rutas = {"/api/sede", "/api/resumen", "/api/pedidos", "/api/inventario", "/api/cuentas", "/api/otra"};
        for (int i = 0; i < rutas.length; i++) {
            HttpResponse<String> r = pedir("GET", rutas[i], null, null, true);
            assertEquals(401, r.statusCode(), rutas[i]);
            assertTrue(json(r).get("error").getAsString().contains("sesión"), rutas[i]);
        }
        // Cookie inventada o de otra sede
        assertEquals(401, pedir("GET", "/api/sede", null, "convitec_ALA=inventada", true).statusCode());
        assertEquals(401, pedir("GET", "/api/sede", null, "convitec_SJO=" + tokenDe(sede.cookieSesion()),
                true).statusCode());
        // Los archivos de la página sí se sirven (la pantalla de inicio de sesión es parte de ella)
        assertEquals(200, pedir("GET", "/", null, null, true).statusCode());
    }

    @Test
    void estadoDeAccesoPublico() throws Exception {
        JsonObject e = json(pedir("GET", "/api/acceso", null, null, true));
        assertEquals("ALA", e.getAsJsonObject("sede").get("codigo").getAsString());
        assertTrue(e.get("necesitaInstalacion").getAsBoolean());
        assertEquals(30, e.get("minutosInactividad").getAsInt());
        assertFalse(e.has("sesion"));
        JsonArray sedes = e.getAsJsonArray("sedes");
        assertEquals(4, sedes.size());
        assertEquals("LIB", sedes.get(0).getAsJsonObject().get("codigo").getAsString());
        assertEquals(8081, sedes.get(0).getAsJsonObject().get("puerto").getAsInt());
        assertTrue(sedes.get(1).getAsJsonObject().get("esEstaSede").getAsBoolean());

        JsonObject conSesion = json(pedir("GET", "/api/acceso", null, sede.cookieSesion(), true));
        assertFalse(conSesion.get("necesitaInstalacion").getAsBoolean());
        assertEquals("prueba", conSesion.getAsJsonObject("sesion").get("usuario").getAsString());
    }

    @Test
    void losPostSinElEncabezadoDeLaPaginaSeRechazan() throws Exception {
        String cuerpo = "{\"usuario\":\"prueba\",\"clave\":\"clave-de-prueba\"}";
        sede.cookieSesion();
        assertEquals(403, pedir("POST", "/api/acceso/entrar", cuerpo, null, false).statusCode());
        assertEquals(403, pedir("POST", "/api/pedidos", "{}", sede.cookieSesion(), false).statusCode());
        assertEquals(200, pedir("POST", "/api/acceso/entrar", cuerpo, null, true).statusCode());
    }

    @Test
    void primeraCuentaConCodigoEntrarYSalir() throws Exception {
        String codigo = sede.acceso.generarCodigoAcceso();
        HttpResponse<String> malo = pedir("POST", "/api/acceso/codigo",
                "{\"codigo\":\"0\",\"nombre\":\"Ana Mora\",\"usuario\":\"ana\",\"clave\":\"clave-de-ana\"}", null, true);
        assertEquals(403, malo.statusCode());

        HttpResponse<String> r = pedir("POST", "/api/acceso/codigo", "{\"codigo\":\"" + codigo
                + "\",\"nombre\":\"Ana Mora\",\"usuario\":\"ana\",\"clave\":\"clave-de-ana\"}", null, true);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("Ana Mora", json(r).get("nombre").getAsString());
        String cookie = cookieDe(r);
        String setCookie = r.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.startsWith("convitec_ALA="), setCookie);
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Strict"), setCookie);

        assertEquals(200, pedir("GET", "/api/sede", null, cookie, true).statusCode());

        // Contraseña incorrecta y correcta
        assertEquals(401, pedir("POST", "/api/acceso/entrar", "{\"usuario\":\"ana\",\"clave\":\"no\"}", null, true)
                .statusCode());
        HttpResponse<String> entrar = pedir("POST", "/api/acceso/entrar",
                "{\"usuario\":\"Ana\",\"clave\":\"clave-de-ana\"}", null, true);
        assertEquals(200, entrar.statusCode());
        String otraCookie = cookieDe(entrar);

        // Salir cierra esa sesión, la otra sigue
        HttpResponse<String> salir = pedir("POST", "/api/acceso/salir", "{}", otraCookie, true);
        assertEquals(200, salir.statusCode());
        assertTrue(salir.headers().firstValue("Set-Cookie").orElse("").contains("Max-Age=0"));
        assertEquals(401, pedir("GET", "/api/sede", null, otraCookie, true).statusCode());
        assertEquals(200, pedir("GET", "/api/sede", null, cookie, true).statusCode());
    }

    @Test
    void demasiadosIntentosDan429() throws Exception {
        sede.cookieSesion();
        String cuerpo = "{\"usuario\":\"prueba\",\"clave\":\"incorrecta\"}";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, pedir("POST", "/api/acceso/entrar", cuerpo, null, true).statusCode());
        }
        HttpResponse<String> r = pedir("POST", "/api/acceso/entrar",
                "{\"usuario\":\"prueba\",\"clave\":\"clave-de-prueba\"}", null, true);
        assertEquals(429, r.statusCode());
        assertEquals("30", r.headers().firstValue("Retry-After").orElse(""));
        assertTrue(json(r).get("error").getAsString().contains("espere"));
    }

    @Test
    void seccionCuentas() throws Exception {
        String cookie = sede.cookieSesion();
        String nueva = "{\"nombre\":\"Luis Soto\",\"usuario\":\"luis\",\"clave\":\"clave-de-luis\","
                + "\"claveConfirmacion\":\"%s\"}";
        assertEquals(403, pedir("POST", "/api/cuentas", String.format(nueva, "mala"), cookie, true).statusCode());
        assertEquals(200, pedir("POST", "/api/cuentas", String.format(nueva, "clave-de-prueba"), cookie, true)
                .statusCode());
        assertEquals(400, pedir("POST", "/api/cuentas", String.format(nueva, "clave-de-prueba"), cookie, true)
                .statusCode()); // repetida

        JsonArray cuentas = JsonParser.parseString(pedir("GET", "/api/cuentas", null, cookie, true).body())
                .getAsJsonArray();
        assertEquals(2, cuentas.size());
        JsonObject luis = cuentas.get(0).getAsJsonObject();
        JsonObject prueba = cuentas.get(1).getAsJsonObject();
        assertEquals("luis", luis.get("usuario").getAsString());
        assertFalse(luis.get("esUsted").getAsBoolean());
        assertTrue(prueba.get("esUsted").getAsBoolean());
        assertFalse(luis.has("hash") || luis.has("sal")); // nunca se envían

        assertEquals(400, pedir("POST", "/api/cuentas/estado",
                "{\"usuario\":\"prueba\",\"activa\":false,\"claveConfirmacion\":\"clave-de-prueba\"}", cookie, true)
                .statusCode()); // la propia no
        assertEquals(200, pedir("POST", "/api/cuentas/estado",
                "{\"usuario\":\"luis\",\"activa\":false,\"claveConfirmacion\":\"clave-de-prueba\"}", cookie, true)
                .statusCode());
        assertEquals(401, pedir("POST", "/api/acceso/entrar", "{\"usuario\":\"luis\",\"clave\":\"clave-de-luis\"}",
                null, true).statusCode());

        assertEquals(200, pedir("POST", "/api/cuentas/clave",
                "{\"usuario\":\"luis\",\"claveNueva\":\"luis-nueva-1\",\"claveConfirmacion\":\"clave-de-prueba\"}",
                cookie, true).statusCode());
    }

    @Test
    void lasActualizacionesAutomaticasSeIdentifican() throws Exception {
        String cookie = sede.cookieSesion();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(servidor.getUrl() + "/api/resumen"))
                .header("Cookie", cookie).header(FiltroSesion.ENCABEZADO_AUTOMATICA, "1").GET().build();
        assertEquals(200, cliente.send(peticion, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    // ------------------------------------------------------------------

    private HttpResponse<String> pedir(String metodo, String ruta, String cuerpo, String cookie, boolean conEncabezado)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(servidor.getUrl() + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        if (conEncabezado) {
            b.header(FiltroSesion.ENCABEZADO_APP, "1");
        }
        return cliente.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> r) {
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    /** "convitec_ALA=abc; Path=/; ..." -> "convitec_ALA=abc". */
    private static String cookieDe(HttpResponse<String> r) {
        String valor = r.headers().firstValue("Set-Cookie").orElseThrow();
        return valor.substring(0, valor.indexOf(';'));
    }

    private static String tokenDe(String cookie) {
        return cookie.substring(cookie.indexOf('=') + 1);
    }
}
