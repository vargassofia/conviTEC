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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

import static cr.ac.tec.convitec.web.ApiMapaEntregasTest.buscar;
import static cr.ac.tec.convitec.web.ApiMapaEntregasTest.json;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas por HTTP de inventario, catálogo, pedidos, estadísticas y resumen, contra una sede completa
 * (servicios reales de la Persona 2 en una carpeta temporal, mensajería falsa).
 */
class ApiNegocioTest {

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

    // ------------------------------------------------------------------
    // Inventario y catálogo
    // ------------------------------------------------------------------

    @Test
    void inventarioMuestraTodoElCatalogoAunqueNoHayaUnidades() throws Exception {
        iniciarSede("ALA");
        JsonArray productos = arreglo(get("/api/inventario"), 200);

        assertEquals(7, productos.size()); // catalogo-inicial.json
        assertEquals("A01", productos.get(0).getAsJsonObject().get("codigo").getAsString());
        for (JsonElement p : productos) {
            assertEquals(0, p.getAsJsonObject().get("disponible").getAsInt());
        }
        JsonObject cafe = buscar(productos, "codigo", "C01");
        assertEquals("Café", cafe.get("nombre").getAsString());
        assertEquals(1500.0, cafe.get("precio").getAsDouble(), 0.001);
    }

    @Test
    void agregarUnidadesActualizaElInventarioYAvisaALasOtrasSedes() throws Exception {
        iniciarSede("ALA");
        JsonObject r = json(post("/api/inventario/agregar", "{\"codigoProducto\":\"c01\",\"cantidad\":10}"), 200);

        assertEquals("C01", r.get("codigoProducto").getAsString());
        assertEquals(10, r.get("disponible").getAsInt());
        assertFalse(r.has("aviso"));
        assertEquals(1, sede.mensajeria.publicaciones);
        assertEquals(10, buscar(arreglo(get("/api/inventario"), 200), "codigo", "C01").get("disponible").getAsInt());

        json(post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\",\"cantidad\":5}"), 200);
        assertEquals(15, buscar(arreglo(get("/api/inventario"), 200), "codigo", "C01").get("disponible").getAsInt());
    }

    @Test
    void siNoSePuedeAvisarLasUnidadesIgualSeAgreganConUnAviso() throws Exception {
        iniciarSede("ALA");
        sede.mensajeria.fallarPublicacion = true;

        JsonObject r = json(post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\",\"cantidad\":4}"), 200);
        assertEquals(4, r.get("disponible").getAsInt());
        assertTrue(r.get("aviso").getAsString().contains("no se pudo avisar"));
        assertEquals(4, buscar(arreglo(get("/api/inventario"), 200), "codigo", "C01").get("disponible").getAsInt());
    }

    @Test
    void agregarUnidadesRechazaDatosInvalidos() throws Exception {
        iniciarSede("ALA");
        assertEquals(400, post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\",\"cantidad\":0}").statusCode());
        assertEquals(400, post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\",\"cantidad\":-3}").statusCode());
        assertEquals(400, post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\"}").statusCode());
        assertEquals(400, post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\",\"cantidad\":\"diez\"}").statusCode());
        assertEquals(400, post("/api/inventario/agregar", "{\"codigoProducto\":\"C01\",\"cantidad\":10001}").statusCode());
        assertEquals(400, post("/api/inventario/agregar", "{\"cantidad\":3}").statusCode());
        assertTrue(json(post("/api/inventario/agregar", "{\"codigoProducto\":\"ZZ9\",\"cantidad\":3}"), 400)
                .get("error").getAsString().contains("ZZ9"));
        assertEquals(0, sede.mensajeria.publicaciones);
        assertEquals(405, get("/api/inventario/agregar").statusCode());
    }

    @Test
    void catalogoGlobalCombinaEstaSedeConLoConocidoDeLasOtras() throws Exception {
        iniciarSede("ALA");
        sede.inventario.agregarUnidades("B01", 2);
        sede.catalogo.actualizarStock("LIM", "B01", 5);

        JsonObject bebida = buscar(arreglo(get("/api/catalogo"), 200), "codigo", "B01");
        JsonArray sedes = bebida.getAsJsonArray("sedes");
        assertEquals(4, sedes.size());
        JsonObject alajuela = buscar(sedes, "codigoSede", "ALA");
        assertTrue(alajuela.get("esEstaSede").getAsBoolean());
        assertEquals(2, alajuela.get("cantidad").getAsInt());
        assertEquals(5, buscar(sedes, "codigoSede", "LIM").get("cantidad").getAsInt());
        // San José todavía no ha avisado su stock: no se conoce (no viene la cantidad)
        assertFalse(buscar(sedes, "codigoSede", "SJO").has("cantidad"));
        assertEquals("San José", buscar(sedes, "codigoSede", "SJO").get("nombreSede").getAsString());
        assertEquals(7, bebida.get("totalConocido").getAsInt());
    }

    // ------------------------------------------------------------------
    // Pedidos
    // ------------------------------------------------------------------

    @Test
    void pedidoConStockLocalSeAcepta() throws Exception {
        iniciarSede("ALA");
        sede.inventario.agregarUnidades("C01", 5);

        JsonObject r = json(post("/api/pedidos",
                "{\"destino\":\"heredia\",\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":2}]}"), 200);
        assertTrue(r.get("aceptado").getAsBoolean());
        JsonObject pedido = r.getAsJsonObject("pedido");
        assertEquals("ALA-0001", pedido.get("id").getAsString());
        assertEquals("Heredia", pedido.get("destino").getAsString()); // nombre oficial
        assertEquals("CONFIRMADO", pedido.get("estado").getAsString());
        assertEquals(3000.0, pedido.get("montoTotal").getAsDouble(), 0.001);
        JsonObject detalle = pedido.getAsJsonArray("detalles").get(0).getAsJsonObject();
        assertEquals("ALA", detalle.get("sedeSurtidora").getAsString());
        assertEquals("Alajuela", detalle.get("nombreSedeSurtidora").getAsString());
        assertNotNull(pedido.getAsJsonObject("salida").get("hora"));

        assertEquals(3, buscar(arreglo(get("/api/inventario"), 200), "codigo", "C01").get("disponible").getAsInt());
        assertEquals(1, arreglo(get("/api/pedidos?estado=pendientes"), 200).size());
    }

    @Test
    void pedidoSinStockSeRechazaConMotivoYQuedaEnElHistorial() throws Exception {
        iniciarSede("ALA");
        JsonObject r = json(post("/api/pedidos",
                "{\"destino\":\"Heredia\",\"lineas\":[{\"codigoProducto\":\"B01\",\"cantidad\":2}]}"), 200);

        assertFalse(r.get("aceptado").getAsBoolean());
        assertTrue(r.get("motivo").getAsString().contains("B01"));
        assertEquals("CANCELADO", r.getAsJsonObject("pedido").get("estado").getAsString());
        assertEquals(0, arreglo(get("/api/pedidos?estado=pendientes"), 200).size());
        assertEquals(1, arreglo(get("/api/pedidos?estado=CANCELADO"), 200).size());
    }

    @Test
    void pedidoConProductosDeOtraSede() throws Exception {
        iniciarSede("ALA");
        sede.catalogo.actualizarStock("LIM", "B01", 5);

        JsonObject r = json(post("/api/pedidos",
                "{\"destino\":\"Heredia\",\"lineas\":[{\"codigoProducto\":\"B01\",\"cantidad\":2}]}"), 200);
        assertTrue(r.get("aceptado").getAsBoolean(), r.toString());
        JsonObject detalle = r.getAsJsonObject("pedido").getAsJsonArray("detalles").get(0).getAsJsonObject();
        assertEquals("LIM", detalle.get("sedeSurtidora").getAsString());
        assertEquals(1, sede.mensajeria.reservasSolicitadas);

        // Si la otra sede rechaza, el pedido se cancela con su motivo
        sede.mensajeria.aceptarReservas = false;
        JsonObject rechazado = json(post("/api/pedidos",
                "{\"destino\":\"Heredia\",\"lineas\":[{\"codigoProducto\":\"B01\",\"cantidad\":1}]}"), 200);
        assertFalse(rechazado.get("aceptado").getAsBoolean());
        assertTrue(rechazado.get("motivo").getAsString().contains("Limón"));
    }

    @Test
    void liberiaNoPuedeEntregarFueraDeSuZona() throws Exception {
        iniciarSede("LIB");
        sede.inventario.agregarUnidades("C01", 5);

        JsonObject r = json(post("/api/pedidos",
                "{\"destino\":\"Cartago\",\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":1}]}"), 200);
        assertFalse(r.get("aceptado").getAsBoolean());
        assertTrue(r.get("motivo").getAsString().contains("ruta"));
        // Las unidades reservadas se liberaron
        assertEquals(5, buscar(arreglo(get("/api/inventario"), 200), "codigo", "C01").get("disponible").getAsInt());

        assertTrue(json(post("/api/pedidos",
                "{\"destino\":\"Cañas\",\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":1}]}"), 200)
                .get("aceptado").getAsBoolean());
    }

    @Test
    void erroresDelFormularioNoCreanPedidos() throws Exception {
        iniciarSede("ALA");
        sede.inventario.agregarUnidades("C01", 5);

        assertEquals(400, post("/api/pedidos", "{\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":1}]}").statusCode());
        assertTrue(json(post("/api/pedidos",
                "{\"destino\":\"Narnia\",\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":1}]}"), 400)
                .get("error").getAsString().contains("Narnia"));
        assertEquals(400, post("/api/pedidos", "{\"destino\":\"Heredia\",\"lineas\":[]}").statusCode());
        assertEquals(400, post("/api/pedidos", "{\"destino\":\"Heredia\"}").statusCode());
        assertEquals(400, post("/api/pedidos",
                "{\"destino\":\"Heredia\",\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":0}]}").statusCode());
        assertEquals(400, post("/api/pedidos",
                "{\"destino\":\"Heredia\",\"lineas\":[{\"codigoProducto\":\"C01\",\"cantidad\":1001}]}").statusCode());
        assertEquals(400, post("/api/pedidos",
                "{\"destino\":\"Heredia\",\"lineas\":[{\"cantidad\":1}]}").statusCode());
        assertEquals(400, post("/api/pedidos", "no es json").statusCode());

        assertEquals(0, arreglo(get("/api/pedidos"), 200).size());
        assertEquals(5, buscar(arreglo(get("/api/inventario"), 200), "codigo", "C01").get("disponible").getAsInt());
    }

    @Test
    void historialDelMasRecienteAlMasAntiguoYFiltros() throws Exception {
        iniciarSede("ALA");
        sede.inventario.agregarUnidades("C01", 5);
        sede.pedidoAceptado("Heredia", "C01", 1);
        sede.pedidoAceptado("San José", "C01", 1);
        post("/api/pedidos", "{\"destino\":\"Heredia\",\"lineas\":[{\"codigoProducto\":\"B01\",\"cantidad\":1}]}");

        JsonArray todos = arreglo(get("/api/pedidos?estado=todos"), 200);
        assertEquals(3, todos.size());
        assertEquals("ALA-0003", todos.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("ALA-0001", todos.get(2).getAsJsonObject().get("id").getAsString());

        JsonArray pendientes = arreglo(get("/api/pedidos?estado=pendientes"), 200);
        assertEquals(2, pendientes.size());
        assertEquals("ALA-0001", pendientes.get(0).getAsJsonObject().get("id").getAsString()); // orden de llegada
        assertEquals(2, arreglo(get("/api/pedidos?estado=confirmado"), 200).size());
        assertTrue(json(get("/api/pedidos?estado=raro"), 400).get("error").getAsString().contains("raro"));
        assertEquals(405, cliente.send(sede.conSesion(HttpRequest.newBuilder(URI.create(servidor.getUrl() + "/api/pedidos")))
                .DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    // ------------------------------------------------------------------
    // Estadísticas y resumen
    // ------------------------------------------------------------------

    @Test
    void estadisticasSinVentas() throws Exception {
        iniciarSede("ALA");
        JsonObject e = json(get("/api/estadisticas"), 200);

        assertEquals(0, e.getAsJsonArray("historial").size());
        assertFalse(e.has("productoEstrella"));
        assertEquals(0, e.getAsJsonArray("ranking").size());
        assertEquals(0, e.getAsJsonArray("peoresPedidos").size());
        assertEquals(0.0, e.get("montoTotalVendido").getAsDouble(), 0.001);
    }

    @Test
    void estadisticasRechazanParametrosInvalidos() throws Exception {
        iniciarSede("ALA");
        assertEquals(200, get("/api/estadisticas?desde=2026-01-01&hasta=2026-12-31&peores=3").statusCode());
        assertEquals(200, get("/api/estadisticas?desde=2026-01-01").statusCode());
        assertEquals(400, get("/api/estadisticas?desde=ayer").statusCode());
        assertEquals(400, get("/api/estadisticas?desde=2026-12-31&hasta=2026-01-01").statusCode());
        assertEquals(400, get("/api/estadisticas?peores=0").statusCode());
        assertEquals(400, get("/api/estadisticas?peores=51").statusCode());
        assertEquals(400, get("/api/estadisticas?peores=muchos").statusCode());
    }

    @Test
    void resumenDelDia() throws Exception {
        iniciarSede("ALA");
        sede.inventario.agregarUnidades("C01", 5);
        sede.pedidoAceptado("Heredia", "C01", 1);

        JsonObject r = json(get("/api/resumen"), 200);
        assertEquals(1, r.get("pedidosPendientes").getAsInt());
        assertEquals(1, r.get("pedidosHoy").getAsInt());
        assertEquals(0, r.get("ventasHoy").getAsInt());
        assertFalse(r.has("productoEstrella"));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static JsonArray arreglo(HttpResponse<String> r, int estadoEsperado) {
        assertEquals(estadoEsperado, r.statusCode(), r.body());
        return JsonParser.parseString(r.body()).getAsJsonArray();
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
