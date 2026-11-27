package cr.ac.tec.convitec.mensajeria;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.DisponibilidadSede;
import cr.ac.tec.convitec.catalogo.ExistenciaInventario;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.catalogo.ServicioInventario;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.entregas.RepositorioVentasArchivo;
import cr.ac.tec.convitec.entregas.ServicioEntregas;
import cr.ac.tec.convitec.estadisticas.ServicioEstadisticas;
import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.pedidos.ResultadoPedido;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.pedidos.SolicitudLinea;
import cr.ac.tec.convitec.persistencia.EstadoSede;
import cr.ac.tec.convitec.persistencia.PersistenciaSede;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Flujos distribuidos completos usando cuatro sedes y un RabbitMQ local real. */
class IntegracionSedesRabbitTest {

    private static final String[] CODIGOS = {"ALA", "LIM", "SJO", "LIB"};
    private static final String[] NOMBRES = {"Alajuela", "Limón", "San José", "Liberia"};
    private static final Producto[] PRODUCTOS = {
            new Producto("C01", "Café", "", "Bebidas", 1500),
            new Producto("B01", "Bebida", "", "Bebidas", 1000),
            new Producto("S01", "Sándwich", "", "Comidas", 2500)
    };

    @TempDir
    Path carpetaTemporal;

    private TopologiaRabbit topologia;
    private NodoIntegracion[] sedes;

    @BeforeEach
    void requerirBroker() {
        assumeTrue(brokerDisponible(), "RabbitMQ no está corriendo en localhost:5672");
        topologia = new TopologiaRabbit(".integracion-" + System.nanoTime());
    }

    @AfterEach
    void cerrarYLimpiarTopologia() {
        if (sedes != null) {
            for (NodoIntegracion sede : sedes) {
                if (sede != null) {
                    sede.cerrar();
                }
            }
        }
        if (topologia == null || !brokerDisponible()) {
            return;
        }
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost("localhost");
        try (Connection conexion = fabrica.newConnection();
             Channel canal = conexion.createChannel()) {
            for (String codigo : CODIGOS) {
                canal.queueDelete(topologia.colaSede(codigo));
                canal.queueDelete(topologia.colaStock(codigo));
            }
            canal.exchangeDelete(topologia.exchangeDirecto());
            canal.exchangeDelete(topologia.exchangeStock());
        } catch (Exception e) {
            System.err.println("No se pudo limpiar la topología de integración: " + e.getMessage());
        }
    }

    @Test
    void pedidoExitosoReservaEnDosSedesYPropagaElStockActualizado() throws Exception {
        NodoIntegracion ala = iniciarRed()[0];
        ResultadoPedido resultado = ala.pedidos.registrarPedido("Heredia", new SolicitudLinea[]{
                new SolicitudLinea("C01", 1), new SolicitudLinea("B01", 2)
        });

        assertTrue(resultado.isAceptado(), resultado.getMotivo());
        assertEquals(EstadoPedido.CONFIRMADO, resultado.getPedido().getEstado());
        assertEquals(2, resultado.getPedido().getDetalles().length);
        assertEquals(1, ala.pedidos.listarPendientes().length);
        assertEquals(2, ala.inventarioDisponible("C01"));
        assertTrue(esperarHasta(() -> ala.catalogoStock("LIM", "B01") == 3, 5000));
        assertTrue(esperarHasta(() -> sedes[1].catalogoStock("ALA", "C01") == 2, 5000));
        assertTrue(esperarHasta(() -> sedes[2].catalogoStock("LIM", "B01") == 3, 5000));
        RespuestaReserva exceso = ala.mensajeria.solicitarReserva("LIM", "VERIFICAR-STOCK",
                "B01", 4);
        assertFalse(exceso.isExito());
        assertTrue(exceso.getMotivo().contains("hay 3"), exceso.getMotivo());
    }

    @Test
    void rechazoPorStockLiberaReservasLocalesYRemotasYQuedaEnHistorial() throws Exception {
        NodoIntegracion ala = iniciarRed()[0];
        int disponibleLocalAntes = ala.inventarioDisponible("C01");

        ResultadoPedido resultado = ala.pedidos.registrarPedido("Heredia", new SolicitudLinea[]{
                new SolicitudLinea("C01", 1), new SolicitudLinea("S01", 10)
        });

        assertFalse(resultado.isAceptado());
        assertEquals(EstadoPedido.CANCELADO, resultado.getPedido().getEstado());
        assertEquals(0, ala.pedidos.listarPendientes().length);
        assertEquals(EstadoPedido.CANCELADO, ala.pedidos.listarHistorial()[0].getEstado());
        assertTrue(esperarHasta(() -> ala.puedeReservarRemotoCompleto("SJO", "S01", 6), 5000));
        assertEquals(disponibleLocalAntes, ala.inventarioDisponible("C01"));
        assertEquals(0, ala.reservado("C01"));
    }

    @Test
    void rechazoPorDestinoInexistenteLiberaLaReservaYLaRutaALIBCartagoDependeDelGrafoReal()
            throws Exception {
        NodoIntegracion ala = iniciarRed()[0];
        NodoIntegracion liberia = sedes[3];
        ResultadoPedido resultado = ala.pedidos.registrarPedido("DestinoInexistente",
                new SolicitudLinea[]{new SolicitudLinea("B01", 1)});

        assertFalse(resultado.isAceptado());
        assertTrue(resultado.getMotivo().toLowerCase().contains("destino"));
        assertTrue(esperarHasta(() -> ala.puedeReservarRemotoCompleto("LIM", "B01", 5), 5000));
        assertFalse(liberia.rutas.existeRuta("Liberia", "Cartago"),
                "El grafo real actualmente no conecta Guanacaste con Cartago.");
    }

    @Test
    void sedeApagadaProvocaTimeoutYAlReabrirNoQuedaReserva() throws Exception {
        iniciarRed(350);
        NodoIntegracion ala = sedes[0];
        NodoIntegracion lim = sedes[1];
        lim.cerrarMensajeria();

        long inicio = System.currentTimeMillis();
        ResultadoPedido resultado = ala.pedidos.registrarPedido("Heredia",
                new SolicitudLinea[]{new SolicitudLinea("B01", 1)});
        long transcurrido = System.currentTimeMillis() - inicio;

        assertFalse(resultado.isAceptado());
        assertTrue(resultado.getMotivo().toLowerCase().contains("no respondió"));
        assertTrue(transcurrido >= ala.config.getTimeoutReservaMs() - 50);
        lim.iniciarMensajeria();
        assertTrue(esperarHasta(() -> ala.puedeReservarRemotoCompleto("LIM", "B01", 5), 5000));
    }

    @Test
    void recorridoPasoAPasoRegistraVentasConTiempoAcumuladoYEntregaPedidos() throws Exception {
        NodoIntegracion ala = iniciarRed()[0];
        ResultadoPedido heredia = ala.pedidos.registrarPedido("Heredia",
                new SolicitudLinea[]{new SolicitudLinea("C01", 1)});
        ResultadoPedido sanJose = ala.pedidos.registrarPedido("San José",
                new SolicitudLinea[]{new SolicitudLinea("C01", 1)});
        assertTrue(heredia.isAceptado());
        assertTrue(sanJose.isAceptado());

        String horaSalida = SalidasDiarias.salidaParaRegistro(LocalTime.of(9, 0)).getHoraSalida();
        ala.rutas.iniciarRecorrido(ala.pedidos.construirSolicitudesEntrega(), horaSalida);
        int ventasCreadas = 0;
        while (ala.rutas.hayEntregasPendientes()) {
            ParadaEntrega parada = ala.rutas.calcularSiguienteEntrega();
            Venta[] ventas = ala.entregas.registrarParada(parada, horaSalida, LocalDateTime.now());
            assertEquals(parada.getTiempoAcumulado(), ventas[0].getTiempoTraslado());
            ventasCreadas += ventas.length;
        }

        assertEquals(2, ventasCreadas);
        assertEquals(0, ala.pedidos.listarPendientes().length);
        assertEquals(EstadoPedido.ENTREGADO, ala.pedidos.buscarPorId(heredia.getPedido().getId()).getEstado());
        assertEquals(EstadoPedido.ENTREGADO, ala.pedidos.buscarPorId(sanJose.getPedido().getId()).getEstado());
        assertEquals(2, ala.ventas.listar().length);
    }

    @Test
    void cerrarYReabrirRestauraEstadoVentasYContinuaIds() throws Exception {
        NodoIntegracion ala = iniciarRed()[0];
        ResultadoPedido pedido = ala.pedidos.registrarPedido("Heredia",
                new SolicitudLinea[]{new SolicitudLinea("C01", 1)});
        ala.rutas.iniciarRecorrido(ala.pedidos.construirSolicitudesEntrega(), "11:00");
        ParadaEntrega parada = ala.rutas.calcularSiguienteEntrega();
        ala.entregas.registrarParada(parada, "11:00", LocalDateTime.now());
        int disponibleAntes = ala.inventarioDisponible("C01");
        ala.cerrar();

        NodoIntegracion reabierta = new NodoIntegracion("ALA", carpetaTemporal.resolve("ALA"),
                null, topologia, 3000);
        sedes[0] = reabierta;
        reabierta.iniciar();

        assertEquals(disponibleAntes, reabierta.inventarioDisponible("C01"));
        assertEquals(pedido.getPedido().getId(), reabierta.pedidos.listarHistorial()[0].getId());
        assertEquals(EstadoPedido.ENTREGADO,
                reabierta.pedidos.listarHistorial()[0].getEstado());
        assertEquals(1, reabierta.ventas.listar().length);
        assertEquals("V-" + pedido.getPedido().getId(), reabierta.ventas.listar()[0].getIdVenta());
        ResultadoPedido siguiente = reabierta.pedidos.registrarPedido("Heredia",
                new SolicitudLinea[]{new SolicitudLinea("C01", 1)});
        assertEquals("ALA-0002", siguiente.getPedido().getId());
    }

    @Test
    void cadaSedePersisteSoloEnSuCarpetaYNoComparteExistencias() throws Exception {
        iniciarRed();
        sedes[0].servicioInventario.agregarUnidades("C01", 4);
        sedes[1].servicioInventario.agregarUnidades("B01", 2);

        assertTrue(sedes[0].suCarpetaPropiaContieneSolo("C01"));
        assertTrue(sedes[1].suCarpetaPropiaContieneSolo("B01"));
        assertNotEquals(sedes[0].persistencia.getCarpeta(), sedes[1].persistencia.getCarpeta());
    }

    @Test
    void unaDeDosSolicitudesConcurrentesGanaLaUltimaUnidadSinDejarReservas() throws Exception {
        NodoIntegracion ala = iniciarRed()[0];
        ala.servicioInventario.descontar("C01", 2);
        CountDownLatch inicio = new CountDownLatch(1);
        FutureTask<ResultadoPedido> primera = nuevaSolicitudConcurrente(ala, inicio);
        FutureTask<ResultadoPedido> segunda = nuevaSolicitudConcurrente(ala, inicio);
        new Thread(primera, "pedido-integracion-1").start();
        new Thread(segunda, "pedido-integracion-2").start();
        inicio.countDown();

        ResultadoPedido resultadoPrimero = primera.get(5, TimeUnit.SECONDS);
        ResultadoPedido resultadoSegundo = segunda.get(5, TimeUnit.SECONDS);

        assertNotEquals(resultadoPrimero.isAceptado(), resultadoSegundo.isAceptado());
        assertEquals(0, ala.inventarioDisponible("C01"));
        assertEquals(0, ala.reservado("C01"));
        assertEquals(1, ala.pedidos.listarPendientes().length);
        assertEquals(2, ala.pedidos.listarHistorial().length);
    }

    private FutureTask<ResultadoPedido> nuevaSolicitudConcurrente(NodoIntegracion sede,
                                                                   CountDownLatch inicio) {
        return new FutureTask<>(() -> {
            inicio.await();
            return sede.pedidos.registrarPedido("Heredia",
                    new SolicitudLinea[]{new SolicitudLinea("C01", 1)});
        });
    }

    private NodoIntegracion[] iniciarRed() throws IOException {
        return iniciarRed(3000);
    }

    private NodoIntegracion[] iniciarRed(int timeoutMs) throws IOException {
        sedes = new NodoIntegracion[CODIGOS.length];
        for (int i = 0; i < CODIGOS.length; i++) {
            sedes[i] = new NodoIntegracion(CODIGOS[i], carpetaTemporal.resolve(CODIGOS[i]),
                    stockInicial(i), topologia, timeoutMs);
        }
        for (NodoIntegracion sede : sedes) {
            sede.iniciar();
        }
        return sedes;
    }

    private static int[] stockInicial(int indiceSede) {
        int[][] stocks = {
                {3, 0, 0},
                {0, 5, 0},
                {0, 0, 6},
                {0, 0, 0}
        };
        return stocks[indiceSede];
    }

    private static boolean esperarHasta(BooleanSupplier condicion, long maximoMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + maximoMs;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condicion.getAsBoolean();
    }

    private static boolean brokerDisponible() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5672), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String nombreDe(String codigo) {
        for (int i = 0; i < CODIGOS.length; i++) {
            if (CODIGOS[i].equals(codigo)) {
                return NOMBRES[i];
            }
        }
        throw new IllegalArgumentException("Código de sede desconocido: " + codigo);
    }

    private static final class NodoIntegracion {
        private final ConfigSede config;
        private final PersistenciaSede persistencia;
        private final CatalogoGlobal catalogo;
        private final InventarioLocal inventario;
        private final MensajeriaRabbit mensajeria;
        private final ServicioPedidos pedidos;
        private final RepositorioVentasArchivo ventas;
        private final ServicioEntregas entregas;
        private final ServicioEstadisticas estadisticas;
        private final ServicioInventario servicioInventario;
        private final ServicioRutas rutas;

        private NodoIntegracion(String codigo, Path carpeta, int[] stockInicial,
                                TopologiaRabbit topologia, int timeoutMs) throws IOException {
            config = new ConfigSede(codigo, nombreDe(codigo), 8080, "localhost", 5672,
                    "guest", "guest", timeoutMs, carpeta.toString());
            persistencia = new PersistenciaSede(carpeta);
            EstadoSede estado;
            if (Files.exists(carpeta.resolve("productos.json"))) {
                estado = persistencia.cargarTodo();
                catalogo = new CatalogoGlobal();
                catalogo.restaurar(estado.getProductos(), estado.getStocksConocidos());
                inventario = new InventarioLocal(catalogo);
                inventario.restaurar(estado.getExistencias(), estado.getReservas());
            } else {
                catalogo = new CatalogoGlobal(PRODUCTOS);
                for (int sede = 0; sede < CODIGOS.length; sede++) {
                    int[] stocks = stockInicial(sede);
                    for (int producto = 0; producto < PRODUCTOS.length; producto++) {
                        catalogo.actualizarStock(CODIGOS[sede], PRODUCTOS[producto].getCodigo(),
                                stocks[producto]);
                    }
                }
                inventario = new InventarioLocal(catalogo);
                for (int i = 0; i < PRODUCTOS.length; i++) {
                    if (stockInicial[i] > 0) {
                        inventario.agregarUnidades(PRODUCTOS[i].getCodigo(), stockInicial[i]);
                    }
                }
                persistencia.guardarCatalogo(catalogo.exportarCatalogo());
                persistencia.guardarStockConocido(catalogo);
                persistencia.guardarInventario(inventario);
                estado = persistencia.cargarTodo();
            }

            mensajeria = new MensajeriaRabbit(config, inventario, catalogo, topologia);
            rutas = new ServicioRutas(CargadorGrafo.cargarDesdeRecurso("rutas.json"), config.getNombre());
            pedidos = new ServicioPedidos(config.getNombre(), config.getCodigo(), inventario,
                    catalogo, mensajeria, rutas);
            pedidos.restaurar(estado.getPedidos(), estado.getContadorIds());
            ventas = new RepositorioVentasArchivo(carpeta);
            entregas = new ServicioEntregas(config.getNombre(), pedidos, ventas);
            estadisticas = new ServicioEstadisticas(config.getNombre(), ventas);
            servicioInventario = new ServicioInventario(inventario, catalogo, mensajeria);
            configurarPersistencia();
        }

        private void configurarPersistencia() {
            inventario.setOyenteCambios(() -> persistencia.guardarInventario(inventario));
            catalogo.setOyenteCambios(() -> {
                persistencia.guardarCatalogo(catalogo.exportarCatalogo());
                persistencia.guardarStockConocido(catalogo);
            });
            pedidos.setOyenteCambios(() -> persistencia.guardarPedidos(pedidos));
        }

        private void iniciar() {
            mensajeria.iniciar();
            for (Producto producto : catalogo.exportarCatalogo()) {
                int disponible = inventario.obtenerDisponible(producto.getCodigo());
                mensajeria.publicarActualizacionStock(producto.getCodigo(), Math.max(disponible, 0));
            }
        }

        private void iniciarMensajeria() {
            mensajeria.iniciar();
        }

        private void cerrarMensajeria() {
            mensajeria.cerrar();
        }

        private void cerrar() {
            persistencia.guardarInventario(inventario);
            persistencia.guardarCatalogo(catalogo.exportarCatalogo());
            persistencia.guardarStockConocido(catalogo);
            persistencia.guardarPedidos(pedidos);
            persistencia.guardarVentas(ventas.listar());
            mensajeria.cerrar();
        }

        private int inventarioDisponible(String codigo) {
            ExistenciaInventario existencia = inventario.consultar(codigo);
            return existencia == null ? 0 : existencia.getCantidadDisponible();
        }

        private int reservado(String codigo) {
            ExistenciaInventario existencia = inventario.consultar(codigo);
            return existencia == null ? 0 : existencia.getCantidadReservada();
        }

        private int catalogoStock(String codigoSede, String codigoProducto) {
            for (DisponibilidadSede disponibilidad : catalogo.disponibilidadDe(codigoProducto)) {
                if (disponibilidad.getCodigoSede().equals(codigoSede)) {
                    return disponibilidad.getCantidadDisponible();
                }
            }
            return -1;
        }

        private boolean puedeReservarRemotoCompleto(String codigoSede, String codigoProducto, int cantidad) {
            String idVerificacion = "VERIFICAR-" + System.nanoTime();
            RespuestaReserva respuesta = mensajeria.solicitarReserva(codigoSede, idVerificacion,
                    codigoProducto, cantidad);
            if (!respuesta.isExito()) {
                return false;
            }
            mensajeria.liberarReserva(codigoSede, idVerificacion, codigoProducto);
            return true;
        }

        private boolean suCarpetaPropiaContieneSolo(String codigoProducto) {
            EstadoSede estado = persistencia.cargarTodo();
            Existencia[] existencias = estado.getExistencias();
            boolean encontrado = false;
            for (Existencia existencia : existencias) {
                if (!codigoProducto.equals(existencia.getCodigoProducto())) {
                    return false;
                }
                encontrado = true;
            }
            return encontrado;
        }
    }
}
