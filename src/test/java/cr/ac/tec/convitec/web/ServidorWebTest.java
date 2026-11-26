package cr.ac.tec.convitec.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Levanta el servidor web de verdad (en un puerto libre) y le hace peticiones HTTP.
 * No necesita RabbitMQ.
 */
class ServidorWebTest {

    private static final ZoneId ZONA = ZoneId.of("America/Costa_Rica");

    private final HttpClient cliente = HttpClient.newHttpClient();
    private ConfigSede config;
    private ServidorWeb servidor;

    @BeforeEach
    void preparar() {
        config = ConfigSede.cargarDesdeRecurso("sedes/sede-ALA.properties");
        servidor = crear(config, 0, relojALas("10:30"));
        servidor.iniciar();
    }

    /** Servidor completo de una sede, con el grafo real y los pendientes de demostración. */
    static ServidorWeb crear(ConfigSede config, int puerto, Clock reloj) {
        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        return new ServidorWeb(config, grafo, new ServicioRutas(grafo, config.getNombre()),
                new PendientesDemo(config.getNombre()), puerto, reloj);
    }

    @AfterEach
    void cerrar() {
        servidor.detener();
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    @Test
    void apiSedeDevuelveLosDatosDeLaSede() throws Exception {
        HttpResponse<String> r = get("/api/sede");

        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonObject json = JsonParser.parseString(r.body()).getAsJsonObject();
        assertEquals("ALA", json.get("codigo").getAsString());
        assertEquals("Alajuela", json.get("nombre").getAsString());
        assertEquals(8082, json.get("puertoHttp").getAsInt());
        assertEquals("11:00", json.getAsJsonArray("salidas").get(0).getAsString());
        assertEquals("15:00", json.getAsJsonArray("salidas").get(1).getAsString());
    }

    @Test
    void apiSedeConservaLasTildes() throws Exception {
        servidor.detener();
        servidor = crear(ConfigSede.cargarDesdeRecurso("sedes/sede-SJO.properties"), 0, relojALas("10:30"));
        servidor.iniciar();

        JsonObject json = JsonParser.parseString(get("/api/sede").body()).getAsJsonObject();
        assertEquals("San José", json.get("nombre").getAsString());
    }

    @Test
    void proximaSalidaSegunLaHora() throws Exception {
        assertProximaSalida("10:30", "11:00", false);
        assertProximaSalida("11:00", "15:00", false);
        assertProximaSalida("14:59", "15:00", false);
        assertProximaSalida("15:00", "11:00", true);
        assertProximaSalida("22:00", "11:00", true);
    }

    @Test
    void metodoNoPermitidoDa405ConMensaje() throws Exception {
        HttpResponse<String> r = enviar("POST", "/api/sede");

        assertEquals(405, r.statusCode());
        assertTrue(r.headers().firstValue("Allow").orElse("").contains("GET"));
        JsonObject json = JsonParser.parseString(r.body()).getAsJsonObject();
        assertEquals(405, json.get("estado").getAsInt());
        assertTrue(json.get("error").getAsString().contains("POST"));
    }

    @Test
    void rutasDeApiInexistentesDan404EnJson() throws Exception {
        for (String ruta : new String[]{"/api/no-existe", "/api/sede/extra", "/api/sedeX"}) {
            HttpResponse<String> r = get(ruta);
            assertEquals(404, r.statusCode(), ruta);
            assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), ruta);
            assertNotNull(JsonParser.parseString(r.body()).getAsJsonObject().get("error"), ruta);
        }
    }

    // ------------------------------------------------------------------
    // Archivos de la interfaz
    // ------------------------------------------------------------------

    @Test
    void laRaizDevuelveLaPaginaPrincipal() throws Exception {
        HttpResponse<String> r = get("/");

        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(r.body().contains("ConviTEC"));
    }

    @Test
    void sirveCssJsEImagenesConSuTipo() throws Exception {
        assertTipo("/css/estilos.css", "text/css");
        assertTipo("/js/app.js", "text/javascript");
        assertTipo("/js/api.js", "text/javascript");
        assertTipo("/img/icono.svg", "image/svg+xml");
        assertTipo("/vendor/leaflet/leaflet.js", "text/javascript");
        assertTipo("/vendor/leaflet/leaflet.css", "text/css");
    }

    @Test
    void archivoInexistenteDa404() throws Exception {
        assertEquals(404, get("/no-existe.html").statusCode());
        assertEquals(404, get("/css/no-existe.css").statusCode());
    }

    @Test
    void noSirveArchivosFueraDeLaCarpetaWeb() throws Exception {
        // %2e%2e = ".." codificado: el servidor lo decodifica y debe rechazarlo
        assertEquals(404, get("/%2e%2e/sedes/sede-ALA.properties").statusCode());
        assertEquals(404, get("/%2e%2e/rutas.json").statusCode());
        // Extensiones no permitidas y carpetas
        assertEquals(404, get("/css").statusCode());
        assertEquals(404, get("/css/").statusCode());
    }

    @Test
    void archivosSoloConGetOHead() throws Exception {
        assertEquals(405, enviar("POST", "/index.html").statusCode());
        assertEquals(200, enviar("HEAD", "/index.html").statusCode());
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Test
    void puertoOcupadoDaUnMensajeClaro() {
        int ocupado = servidor.getPuerto();
        ServidorWeb otro = crear(config, ocupado, relojALas("10:30"));

        ServidorWebException e = assertThrows(ServidorWebException.class, otro::iniciar);
        assertTrue(e.getMessage().contains(String.valueOf(ocupado)));
        assertTrue(e.getMessage().contains("http.puerto"));
        assertFalse(otro.estaIniciado());
    }

    @Test
    void detenerDosVecesNoFallaYLiberaElPuerto() {
        int puerto = servidor.getPuerto();
        servidor.detener();
        servidor.detener();
        assertFalse(servidor.estaIniciado());
        assertThrows(IllegalStateException.class, servidor::getPuerto);

        // El puerto quedó libre: otro servidor lo puede usar
        servidor = crear(config, puerto, relojALas("10:30"));
        servidor.iniciar();
        assertEquals(puerto, servidor.getPuerto());
    }

    @Test
    void iniciarDosVecesNoAbreOtroPuerto() {
        int puerto = servidor.getPuerto();
        servidor.iniciar();
        assertEquals(puerto, servidor.getPuerto());
        assertEquals("http://localhost:" + puerto, servidor.getUrl());
    }

    @Test
    void datosInvalidos() {
        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        ServicioRutas rutas = new ServicioRutas(grafo, "Alajuela");
        PendientesDemo demo = new PendientesDemo("Alajuela");
        Clock reloj = Clock.systemDefaultZone();

        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(null, grafo, rutas, demo));
        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(config, null, rutas, demo));
        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(config, grafo, null, demo));
        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(config, grafo, rutas, null));
        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(config, grafo, rutas, demo, -1, reloj));
        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(config, grafo, rutas, demo, 70000, reloj));
        assertThrows(IllegalArgumentException.class, () -> new ServidorWeb(config, grafo, rutas, demo, 0, null));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void assertProximaSalida(String ahora, String hora, boolean diaSiguiente) throws Exception {
        servidor.detener();
        servidor = crear(config, 0, relojALas(ahora));
        servidor.iniciar();

        JsonObject salida = JsonParser.parseString(get("/api/sede").body()).getAsJsonObject()
                .getAsJsonObject("proximaSalida");
        assertEquals(hora, salida.get("hora").getAsString(), "a las " + ahora);
        assertEquals(diaSiguiente, salida.get("diaSiguiente").getAsBoolean(), "a las " + ahora);
    }

    private void assertTipo(String ruta, String tipo) throws Exception {
        HttpResponse<String> r = get(ruta);
        assertEquals(200, r.statusCode(), ruta);
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith(tipo), ruta);
        assertFalse(r.body().isEmpty(), ruta);
    }

    private HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
        return enviar("GET", ruta);
    }

    private HttpResponse<String> enviar(String metodo, String ruta) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(servidor.getUrl() + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private static Clock relojALas(String hora) {
        Instant instante = java.time.LocalDate.of(2026, 11, 25).atTime(java.time.LocalTime.parse(hora))
                .atZone(ZONA).toInstant();
        return Clock.fixed(instante, ZONA);
    }
}
