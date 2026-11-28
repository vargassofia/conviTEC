package cr.ac.tec.convitec.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas por HTTP de /api/grafo, /api/ruta y /api/entregas, con el grafo real (rutas.json),
 * las coordenadas reales (ubicaciones.json) y pedidos REALES registrados en el servicio de pedidos.
 */
class ApiMapaEntregasTest {

    private final HttpClient cliente = HttpClient.newHttpClient();
    private SedeWebDePrueba sede;
    private ServidorWeb servidor;

    @TempDir
    Path carpeta;

    @AfterEach
    void cerrar() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    private void iniciarSede(String codigo) {
        sede = SedeWebDePrueba.crear(codigo, carpeta.resolve(codigo));
        servidor = sede.servidor(0, Clock.systemDefaultZone());
        servidor.iniciar();
    }

    /**
     * San José con 4 pedidos de Café (₡1500) aceptados, en este orden:
     * SJO-0001 Limón (1), SJO-0002 Heredia (2), SJO-0003 Cartago (1), SJO-0004 Heredia (1).
     */
    private void sanJoseConCuatroPedidos() {
        iniciarSede("SJO");
        sede.inventario.agregarUnidades("C01", 20);
        sede.pedidoAceptado("Limón", "C01", 1);
        sede.pedidoAceptado("Heredia", "C01", 2);
        sede.pedidoAceptado("Cartago", "C01", 1);
        sede.pedidoAceptado("Heredia", "C01", 1);
    }

    // ------------------------------------------------------------------
    // /api/grafo
    // ------------------------------------------------------------------

    @Test
    void grafoTraeTodasLasUbicacionesConCoordenadasYLasRutas() throws Exception {
        iniciarSede("ALA");
        JsonObject mapa = json(get("/api/grafo"), 200);

        assertEquals("Alajuela", mapa.get("sede").getAsString());
        assertEquals(13, mapa.getAsJsonArray("ubicaciones").size());
        assertEquals(15, mapa.getAsJsonArray("rutas").size());
        assertEquals(0, mapa.getAsJsonArray("sinCoordenadas").size());

        JsonObject r05 = buscar(mapa.getAsJsonArray("rutas"), "id", "R05");
        assertEquals(35, r05.get("tiempo").getAsInt());

        JsonObject alajuela = buscar(mapa.getAsJsonArray("ubicaciones"), "nombre", "Alajuela");
        assertTrue(alajuela.get("esEstaSede").getAsBoolean());
        assertEquals("ALA", alajuela.get("codigoSede").getAsString());
        assertTrue(alajuela.get("lat").getAsDouble() > 9 && alajuela.get("lat").getAsDouble() < 11);

        JsonObject heredia = buscar(mapa.getAsJsonArray("ubicaciones"), "nombre", "Heredia");
        assertFalse(heredia.get("esEstaSede").getAsBoolean());
        // Gson omite los campos nulos: una ubicación que no es sede no trae codigoSede
        assertFalse(heredia.has("codigoSede") && !heredia.get("codigoSede").isJsonNull());

        assertEquals("LIM", buscar(mapa.getAsJsonArray("ubicaciones"), "nombre", "Limón").get("codigoSede").getAsString());

        // Orden alfabético sin tildes: Alajuela, Cañas, Cartago, ..., Turrialba
        JsonArray ubicaciones = mapa.getAsJsonArray("ubicaciones");
        assertEquals("Alajuela", ubicaciones.get(0).getAsJsonObject().get("nombre").getAsString());
        assertEquals("Cañas", ubicaciones.get(1).getAsJsonObject().get("nombre").getAsString());
        assertEquals("Cartago", ubicaciones.get(2).getAsJsonObject().get("nombre").getAsString());
        assertEquals("Turrialba", ubicaciones.get(12).getAsJsonObject().get("nombre").getAsString());
    }

    @Test
    void grafoMarcaLaZonaAisladaDeGuanacaste() throws Exception {
        iniciarSede("ALA");
        JsonArray ubicaciones = json(get("/api/grafo"), 200).getAsJsonArray("ubicaciones");

        JsonObject liberia = buscar(ubicaciones, "nombre", "Liberia");
        JsonObject canas = buscar(ubicaciones, "nombre", "Cañas");
        JsonObject alajuela = buscar(ubicaciones, "nombre", "Alajuela");
        JsonObject limon = buscar(ubicaciones, "nombre", "Limón");

        assertFalse(liberia.get("alcanzable").getAsBoolean());
        assertFalse(canas.get("alcanzable").getAsBoolean());
        assertTrue(limon.get("alcanzable").getAsBoolean());
        assertEquals(liberia.get("componente").getAsInt(), canas.get("componente").getAsInt());
        assertNotEquals(liberia.get("componente").getAsInt(), alajuela.get("componente").getAsInt());
        assertEquals(alajuela.get("componente").getAsInt(), limon.get("componente").getAsInt());
    }

    @Test
    void desdeLiberiaSoloSeAlcanzaCanas() throws Exception {
        iniciarSede("LIB");
        JsonArray ubicaciones = json(get("/api/grafo"), 200).getAsJsonArray("ubicaciones");

        int alcanzables = 0;
        for (JsonElement e : ubicaciones) {
            if (e.getAsJsonObject().get("alcanzable").getAsBoolean()) {
                alcanzables++;
            }
        }
        assertEquals(2, alcanzables); // Liberia y Cañas
        assertTrue(buscar(ubicaciones, "nombre", "Cañas").get("alcanzable").getAsBoolean());
    }

    @Test
    void componentesConexas() {
        cr.ac.tec.convitec.grafo.Grafo g = new cr.ac.tec.convitec.grafo.Grafo();
        g.agregarArista("A", "B", 5);
        g.agregarArista("C", "D", 5);
        g.agregarVertice("E");

        int[] c = MapaDTO.componentes(g);
        assertEquals(c[g.indiceDe("A")], c[g.indiceDe("B")]);
        assertEquals(c[g.indiceDe("C")], c[g.indiceDe("D")]);
        assertNotEquals(c[g.indiceDe("A")], c[g.indiceDe("C")]);
        assertNotEquals(c[g.indiceDe("E")], c[g.indiceDe("A")]);
        assertNotEquals(c[g.indiceDe("E")], c[g.indiceDe("C")]);
    }

    // ------------------------------------------------------------------
    // /api/ruta
    // ------------------------------------------------------------------

    @Test
    void rutaMasCortaDesdeLaSede() throws Exception {
        iniciarSede("ALA");
        JsonObject ruta = json(get("/api/ruta?destino=" + codificar("Limón")), 200);

        assertTrue(ruta.get("alcanzable").getAsBoolean());
        assertEquals(215, ruta.get("tiempoTotal").getAsInt());
        assertEquals("Alajuela", ruta.getAsJsonArray("camino").get(0).getAsString());
        assertEquals("Limón", ruta.getAsJsonArray("camino").get(5).getAsString());
    }

    @Test
    void rutaIgnoraTildesYMayusculas() throws Exception {
        iniciarSede("SJO");
        JsonObject ruta = json(get("/api/ruta?destino=limon"), 200);
        assertEquals("Limón", ruta.get("destino").getAsString());
        assertEquals(180, ruta.get("tiempoTotal").getAsInt());
    }

    @Test
    void rutaInalcanzableNoEsErrorYTraeExplicacion() throws Exception {
        iniciarSede("LIB");
        JsonObject ruta = json(get("/api/ruta?destino=Cartago"), 200);

        assertFalse(ruta.get("alcanzable").getAsBoolean());
        assertEquals(0, ruta.getAsJsonArray("camino").size());
        assertTrue(ruta.get("explicacion").getAsString().contains("Liberia"));
    }

    @Test
    void rutaConDestinoInexistenteOVacio() throws Exception {
        iniciarSede("ALA");
        assertTrue(json(get("/api/ruta?destino=Narnia"), 422).get("error").getAsString().contains("Narnia"));
        assertNotNull(json(get("/api/ruta"), 400).get("error"));
        assertNotNull(json(get("/api/ruta?destino="), 400).get("error"));
    }

    // ------------------------------------------------------------------
    // /api/entregas
    // ------------------------------------------------------------------

    @Test
    void antesDeIniciarElVehiculoEstaEnLaSede() throws Exception {
        iniciarSede("SJO");
        JsonObject estado = json(get("/api/entregas"), 200);

        assertFalse(estado.get("iniciado").getAsBoolean());
        assertEquals("San José", estado.get("ubicacionActual").getAsString());
        assertFalse(estado.get("esDemostracion").getAsBoolean());
        assertTrue(json(post("/api/entregas/siguiente", ""), 409).get("error").getAsString().contains("Iniciar"));
    }

    @Test
    void sinPedidosLaSalidaNoTieneParadas() throws Exception {
        iniciarSede("SJO");
        JsonObject estado = json(post("/api/entregas/iniciar", "{\"horaSalida\":\"11:00\"}"), 200);
        assertTrue(estado.get("iniciado").getAsBoolean());
        assertEquals(0, estado.get("totalPedidos").getAsInt());
        assertFalse(estado.get("hayPendientes").getAsBoolean());
        assertEquals(409, post("/api/entregas/siguiente", "").statusCode());
    }

    @Test
    void cadaParadaEntregaYRegistraLasVentas() throws Exception {
        sanJoseConCuatroPedidos();
        JsonObject estado = json(post("/api/entregas/iniciar", "{\"horaSalida\":\"11:00\"}"), 200);
        assertEquals(4, estado.get("totalPedidos").getAsInt());
        assertEquals(4, estado.getAsJsonArray("pendientes").size());

        // Parada 1: Heredia (20 min), entrega juntos SJO-0002 (2 cafés) y SJO-0004 (1 café)
        estado = json(post("/api/entregas/siguiente", ""), 200);
        JsonObject parada = estado.getAsJsonObject("ultimaParada");
        assertEquals("Heredia", parada.get("destino").getAsString());
        assertEquals(20, parada.get("tiempoAcumulado").getAsInt());
        JsonArray ventas = estado.getAsJsonArray("ventasRegistradas");
        assertEquals(2, ventas.size());
        assertEquals("V-SJO-0002", ventas.get(0).getAsJsonObject().get("idVenta").getAsString());
        assertEquals(3000.0, ventas.get(0).getAsJsonObject().get("montoTotal").getAsDouble(), 0.001);
        assertEquals(1500.0, ventas.get(1).getAsJsonObject().get("montoTotal").getAsDouble(), 0.001);
        assertFalse(estado.has("aviso") && !estado.get("aviso").isJsonNull());

        // Los pedidos entregados ya no están pendientes y sus ventas aparecen en las estadísticas
        assertEquals(2, sede.pedidos.listarPendientes().length);
        assertEquals("ENTREGADO", sede.pedidos.buscarPorId("SJO-0002").getEstado().name());
        JsonObject estadisticas = json(get("/api/estadisticas"), 200);
        assertEquals(2, estadisticas.getAsJsonArray("historial").size());
        assertEquals(4500.0, estadisticas.get("montoTotalVendido").getAsDouble(), 0.001);

        // Parada 2: Cartago (acumulado 85); parada 3: Limón (acumulado 220) y termina
        assertEquals("Cartago", json(post("/api/entregas/siguiente", ""), 200)
                .getAsJsonObject("ultimaParada").get("destino").getAsString());
        estado = json(post("/api/entregas/siguiente", ""), 200);
        assertEquals("Limón", estado.getAsJsonObject("ultimaParada").get("destino").getAsString());
        assertEquals(220, estado.get("tiempoAcumulado").getAsInt());
        assertFalse(estado.get("hayPendientes").getAsBoolean());
        assertEquals(3, estado.getAsJsonArray("paradas").size());
        assertEquals(0, sede.pedidos.listarPendientes().length);

        assertTrue(json(post("/api/entregas/siguiente", ""), 409).get("error").getAsString().contains("no quedan"));
        assertEquals(220, json(get("/api/entregas"), 200).get("tiempoAcumulado").getAsInt());

        // Una salida nueva ya no incluye los pedidos entregados
        assertEquals(0, json(post("/api/entregas/iniciar", "{\"horaSalida\":\"15:00\"}"), 200)
                .get("totalPedidos").getAsInt());
    }

    @Test
    void vistaPreviaNoEntregaNadaNiInterrumpeLaSalida() throws Exception {
        sanJoseConCuatroPedidos();
        post("/api/entregas/iniciar", "{\"horaSalida\":\"11:00\"}");
        post("/api/entregas/siguiente", ""); // Heredia: entrega 2 pedidos

        JsonObject vista = json(post("/api/entregas/vista-previa", "{\"horaSalida\":\"11:00\"}"), 200);
        // Los 2 que faltan, desde la sede: Cartago (45) y luego Limón (45 + 135 = 180)
        assertEquals(2, vista.getAsJsonArray("paradas").size());
        assertEquals(180, vista.get("tiempoTotal").getAsInt());
        assertEquals(2, vista.get("pedidosEntregados").getAsInt());

        // Nada cambió: siguen 2 pendientes, 2 ventas, y la salida sigue en Heredia
        assertEquals(2, sede.pedidos.listarPendientes().length);
        assertEquals(2, json(get("/api/estadisticas"), 200).getAsJsonArray("historial").size());
        JsonObject estado = json(get("/api/entregas"), 200);
        assertEquals("Heredia", estado.get("ubicacionActual").getAsString());
        assertTrue(estado.get("hayPendientes").getAsBoolean());
    }

    @Test
    void iniciarDeNuevoReiniciaLaSalida() throws Exception {
        sanJoseConCuatroPedidos();
        post("/api/entregas/iniciar", "{\"horaSalida\":\"11:00\"}");
        post("/api/entregas/siguiente", "");

        JsonObject estado = json(post("/api/entregas/iniciar", "{\"horaSalida\":\"15:00\"}"), 200);
        assertEquals("15:00", estado.get("horaSalida").getAsString());
        assertEquals(0, estado.get("entregados").getAsInt());
        assertEquals(2, estado.get("totalPedidos").getAsInt()); // los 2 que no se habían entregado
        assertEquals("San José", estado.get("ubicacionActual").getAsString());
        assertTrue(estado.get("ultimaParada") == null || estado.get("ultimaParada").isJsonNull());
    }

    @Test
    void datosInvalidosEnEntregas() throws Exception {
        iniciarSede("SJO");
        assertTrue(json(post("/api/entregas/iniciar", "{\"horaSalida\":\"12:00\"}"), 400).get("error")
                .getAsString().contains("11:00"));
        assertNotNull(json(post("/api/entregas/iniciar", "{}"), 400).get("error"));
        assertNotNull(json(post("/api/entregas/iniciar", ""), 400).get("error"));
        assertNotNull(json(post("/api/entregas/iniciar", "esto no es json"), 400).get("error"));
        assertNotNull(json(post("/api/entregas/vista-previa", "{\"horaSalida\":\"9:00\"}"), 400).get("error"));
        assertEquals(405, get("/api/entregas/iniciar").statusCode());
        assertEquals(405, post("/api/entregas", "").statusCode());
        assertEquals(404, get("/api/entregas/otra").statusCode());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    static JsonObject buscar(JsonArray arreglo, String campo, String valor) {
        for (JsonElement e : arreglo) {
            JsonObject o = e.getAsJsonObject();
            if (o.has(campo) && !o.get(campo).isJsonNull() && valor.equals(o.get(campo).getAsString())) {
                return o;
            }
        }
        fail("No se encontró " + campo + " = " + valor);
        return null;
    }

    static JsonObject json(HttpResponse<String> r, int estadoEsperado) {
        assertEquals(estadoEsperado, r.statusCode(), r.body());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
        return cliente.send(sede.conSesion(HttpRequest.newBuilder(URI.create(servidor.getUrl() + ruta))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String ruta, String cuerpo) throws IOException, InterruptedException {
        return cliente.send(sede.conSesion(HttpRequest.newBuilder(URI.create(servidor.getUrl() + ruta)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
