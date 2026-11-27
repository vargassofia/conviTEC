package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.demo.CatalogoGlobalDemo;
import cr.ac.tec.convitec.demo.InventarioDemo;
import cr.ac.tec.convitec.entregas.RepositorioVentasMemoria;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.persistencia.PersistenciaSede;
import cr.ac.tec.convitec.servicios.IMensajeria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConsolaSedeTest {

    @TempDir
    java.nio.file.Path carpetaTemporal;

    /** Mensajería falsa: no usa RabbitMQ, solo anota lo que se le pide. */
    static class MensajeriaFalsa implements IMensajeria {
        int inicios;
        int cierres;
        int actualizaciones;
        String ultimaSolicitud;
        String ultimaConfirmacion;
        String ultimaLiberacion;
        String ultimaActualizacion;

        @Override
        public void iniciar() {
            inicios++;
        }

        @Override
        public void cerrar() {
            cierres++;
        }

        @Override
        public RespuestaReserva solicitarReserva(String sedeDestino, String idPedido, String codigoProducto,
                                                 int cantidad) {
            ultimaSolicitud = sedeDestino + " " + idPedido + " " + codigoProducto + " " + cantidad;
            return RespuestaReserva.ok(sedeDestino);
        }

        @Override
        public void confirmarReserva(String sedeDestino, String idPedido, String codigoProducto) {
            ultimaConfirmacion = sedeDestino + " " + idPedido + " " + codigoProducto;
        }

        @Override
        public void liberarReserva(String sedeDestino, String idPedido, String codigoProducto) {
            ultimaLiberacion = sedeDestino + " " + idPedido + " " + codigoProducto;
        }

        @Override
        public void publicarActualizacionStock(String codigoProducto, int cantidadNueva) {
            actualizaciones++;
            ultimaActualizacion = codigoProducto + " " + cantidadNueva;
        }
    }

    private MensajeriaFalsa mensajeria;
    private Sede sede;

    @BeforeEach
    void preparar() {
        mensajeria = new MensajeriaFalsa();
        ConfigSede config = ConfigSede.cargarDesdeRecurso("sedes/sede-ALA.properties");
        sede = new Sede(config, InventarioDemo.paraSede("ALA"), new CatalogoGlobalDemo(),
                new RepositorioVentasMemoria(), mensajeria);
    }

    /** Ejecuta la consola con esas líneas de entrada y devuelve todo lo que imprimió. */
    private String ejecutar(String... lineas) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        BufferedReader entrada = new BufferedReader(new StringReader(String.join("\n", lineas)));
        new ConsolaSede(sede, entrada, salida).ejecutar();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ayuda() {
        String salida = ejecutar("ayuda", "salir");
        assertTrue(salida.contains("Comandos:"));
        assertTrue(salida.contains("entregar"));
    }

    @Test
    void stock() {
        String salida = ejecutar("stock", "salir");
        assertTrue(salida.contains("Inventario de Alajuela"));
        assertTrue(salida.contains("C01"));
        assertTrue(salida.contains("Café"));
        assertTrue(salida.contains("disponible   8"));
    }

    @Test
    void agregarActualizaYAvisaALasOtrasSedes() {
        String salida = ejecutar("agregar C01 5", "salir");
        assertTrue(salida.contains("Stock de C01: 13"));
        assertEquals("C01 13", mensajeria.ultimaActualizacion);
        assertEquals(13, ((InventarioDemo) sede.getInventario()).getStock("C01"));

        String error = ejecutar("agregar X99 5", "agregar C01 muchas", "salir");
        assertTrue(error.contains("no existe"));
        assertTrue(error.contains("no es un número"));
    }

    @Test
    void rutaALimon() {
        String salida = ejecutar("ruta Limón", "ruta San_Jose", "ruta \"San José\"", "salir");
        assertTrue(salida.contains("Alajuela -> San José -> Cartago -> Paraíso -> Turrialba -> Limón (215 min)"));
        assertTrue(salida.contains("Alajuela -> San José (35 min)"));
    }

    @Test
    void rutaAUnLugarInexistenteNoTermina() {
        String salida = ejecutar("ruta Narnia", "ayuda", "salir");
        assertTrue(salida.contains("Error:"));
        assertTrue(salida.contains("Comandos:"));
    }

    @Test
    void entregar() {
        String salida = ejecutar("entregar 11:00 P1:Puntarenas P2:Orotina P3:Heredia", "salir");
        assertTrue(salida.contains("Recorrido desde Alajuela (salida 11:00)"));
        assertTrue(salida.contains("Tiempo total: 140 min"));

        String error = ejecutar("entregar 09:00 P1:Heredia", "entregar 11:00 P1-Heredia", "salir");
        assertTrue(error.contains("Hora de salida inválida"));
        assertTrue(error.contains("idPedido:destino"));
    }

    @Test
    void reservarConfirmarYLiberarUsanLaMensajeria() {
        String salida = ejecutar("reservar lim P7 B01 2", "confirmar LIM P7 B01", "liberar LIM P8 B01", "salir");
        assertEquals("LIM P7 B01 2", mensajeria.ultimaSolicitud);
        assertEquals("LIM P7 B01", mensajeria.ultimaConfirmacion);
        assertEquals("LIM P8 B01", mensajeria.ultimaLiberacion);
        assertTrue(salida.contains("Reserva OK"));
    }

    @Test
    void comandoDesconocidoNoTermina() {
        String salida = ejecutar("volar a Marte", "reservar LIM P7", "stock", "salir");
        assertTrue(salida.contains("Comando desconocido: 'volar'"));
        assertTrue(salida.contains("necesita 4 argumento"));
        assertTrue(salida.contains("Inventario de Alajuela")); // siguió después de los errores
    }

    @Test
    void salirTermina() {
        String salida = ejecutar("salir", "stock");
        assertTrue(salida.contains("Cerrando la sede ALA"));
        assertFalse(salida.contains("Inventario de Alajuela")); // lo de después de salir no se ejecuta

        ConsolaSede consola = new ConsolaSede(sede, new BufferedReader(new StringReader("")),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertFalse(consola.procesarLinea("SALIR"));
        assertTrue(consola.procesarLinea(""));
    }

    @Test
    void finDeLaEntradaTerminaSinError() {
        assertDoesNotThrow(() -> ejecutar("stock"));
    }

    @Test
    void comandosDePedidosInventarioYEstadisticasUsanServiciosReales() {
        Producto producto = new Producto("C01", "Café", "", "Bebidas", 1500);
        PersistenciaSede persistencia = new PersistenciaSede(carpetaTemporal);
        persistencia.guardarCatalogo(new Producto[]{producto});
        persistencia.guardarExistencias(new Existencia[]{new Existencia("C01", 8)});
        ConfigSede original = ConfigSede.cargarDesdeRecurso("sedes/sede-ALA.properties");
        ConfigSede config = new ConfigSede(original.getCodigo(), original.getNombre(), original.getPuertoHttp(),
                original.getRabbitHost(), original.getRabbitPuerto(), original.getRabbitUsuario(),
                original.getRabbitClave(), original.getTimeoutReservaMs(), carpetaTemporal.toString());
        MensajeriaFalsa mensajeriaReal = new MensajeriaFalsa();
        Sede sedeReal = Sede.crearDesdeConfiguracion(config, mensajeriaReal);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConsolaSede consola = new ConsolaSede(sedeReal,
                new BufferedReader(new StringReader("")),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        try {
            sedeReal.iniciar();
            consola.procesarLinea("stock");
            consola.procesarLinea("catalogo");
            consola.procesarLinea("agregar C01 2");
            consola.procesarLinea("pedido Heredia C01:2");
            consola.procesarLinea("pendientes");
            consola.procesarLinea("historial");
            consola.procesarLinea("detalle ALA-0001");
            consola.procesarLinea("entregar");
            consola.procesarLinea("ventas");
            consola.procesarLinea("estrella");
            consola.procesarLinea("peores");
            consola.procesarLinea("pedido Heredia C01:0");
            consola.procesarLinea("pedido Heredia C01:-2");
            consola.procesarLinea("pedido Heredia malformado");

            String salida = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(salida.contains("₡"));
            assertTrue(salida.contains("Pedido ALA-0001 aceptado"));
            assertTrue(salida.contains("Pedidos pendientes"));
            assertTrue(salida.contains("Historial de pedidos"));
            assertTrue(salida.contains("surte ALA"));
            assertTrue(salida.contains("Entregas registradas: 1"));
            assertTrue(salida.contains("Venta V-ALA-0001"));
            assertTrue(salida.contains("Producto estrella"));
            assertTrue(salida.contains("Peores pedidos"));
            assertTrue(salida.contains("rechazado:"));
            assertTrue(salida.contains("codigo:cantidad"));
            assertEquals(8, sedeReal.getInventario() instanceof InventarioLocal
                    ? ((InventarioLocal) sedeReal.getInventario()).exportarExistencias()[0].getCantidadDisponible()
                    : -1);
        } finally {
            sedeReal.cerrar();
        }
    }

    @Test
    void sedeIniciarYCerrarDosVeces() {
        sede.iniciar();
        sede.iniciar();
        assertEquals(1, mensajeria.inicios);
        sede.cerrar();
        sede.cerrar();
        assertEquals(1, mensajeria.cierres);
        assertThrows(IllegalStateException.class, sede::iniciar);
    }

    @Test
    void sedeConNombreQueNoEstaEnElGrafo() {
        ConfigSede config = new ConfigSede("ALA", "Narnia", 8082, "localhost", 5672, "guest", "guest", 5000, "datos/ALA");
        assertThrows(GrafoException.class, () -> new Sede(config, InventarioDemo.paraSede("ALA"),
                new CatalogoGlobalDemo(), new RepositorioVentasMemoria(), new MensajeriaFalsa()));
        assertThrows(IllegalArgumentException.class, () -> new Sede(config, null,
                new CatalogoGlobalDemo(), new RepositorioVentasMemoria(), new MensajeriaFalsa()));
    }
}
