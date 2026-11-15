package cr.ac.tec.convitec.estadisticas;

import cr.ac.tec.convitec.entregas.RepositorioVentasMemoria;
import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServicioEstadisticasTest {

    private RepositorioVentasMemoria repositorio;
    private ServicioEstadisticas estadisticas;

    private static Venta venta(String idPedido, String sede, LocalDateTime fecha, int tiempo, DetalleVenta... detalles) {
        return new Venta("V-" + idPedido, idPedido, sede, "Heredia", fecha, "11:00", detalles, tiempo);
    }

    private static DetalleVenta d(String codigo, String nombre, int cantidad, double precio) {
        return new DetalleVenta(codigo, nombre, cantidad, precio);
    }

    private static String[] idsPedido(Venta[] ventas) {
        String[] ids = new String[ventas.length];
        for (int i = 0; i < ventas.length; i++) {
            ids[i] = ventas[i].getIdPedido();
        }
        return ids;
    }

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioVentasMemoria();
        estadisticas = new ServicioEstadisticas("San José", repositorio);
    }

    /** Las 4 ventas del escenario del enunciado. */
    private void cargarEscenario() {
        repositorio.guardar(venta("P1", "San José", LocalDateTime.of(2026, 11, 14, 11, 20), 20,
                d("C01", "Café", 2, 1500), d("G01", "Galletas", 1, 800)));           // 3800
        repositorio.guardar(venta("P2", "San José", LocalDateTime.of(2026, 11, 14, 12, 25), 85,
                d("C01", "Café", 1, 1500)));                                          // 1500
        repositorio.guardar(venta("P3", "San José", LocalDateTime.of(2026, 11, 14, 14, 40), 220,
                d("G01", "Galletas", 3, 800), d("A01", "Agua", 1, 600)));             // 3000
        repositorio.guardar(venta("P4", "San José", LocalDateTime.of(2026, 11, 15, 11, 0), 0,
                d("A01", "Agua", 4, 600)));                                           // 2400
    }

    // ------------------------------------------------------------------
    // Escenario principal
    // ------------------------------------------------------------------

    @Test
    void productoEstrellaGanaPorMontoNoPorUnidades() {
        cargarEscenario();
        ProductoEstrella estrella = estadisticas.productoEstrella();
        assertEquals("C01", estrella.getCodigoProducto());
        assertEquals("Café", estrella.getNombreProducto());
        assertEquals(3, estrella.getUnidadesVendidas());
        assertEquals(4500.0, estrella.getMontoTotal());
        assertTrue(estrella.toString().startsWith("Producto estrella"));
    }

    @Test
    void rankingDeProductos() {
        cargarEscenario();
        ResumenProducto[] ranking = estadisticas.rankingProductos();
        assertEquals(3, ranking.length);

        assertEquals("C01", ranking[0].getCodigoProducto());
        assertEquals(4500.0, ranking[0].getMontoTotal());
        assertEquals(3, ranking[0].getUnidadesVendidas());

        assertEquals("G01", ranking[1].getCodigoProducto());
        assertEquals(3200.0, ranking[1].getMontoTotal());
        assertEquals(4, ranking[1].getUnidadesVendidas());

        assertEquals("A01", ranking[2].getCodigoProducto());
        assertEquals(3000.0, ranking[2].getMontoTotal());
        assertEquals(5, ranking[2].getUnidadesVendidas()); // más unidades, pero menos monto
    }

    @Test
    void peoresPedidos() {
        cargarEscenario();
        assertArrayEquals(new String[]{"P3", "P2"}, idsPedido(estadisticas.peoresPedidos(2)));
        assertArrayEquals(new String[]{"P3", "P2", "P1", "P4"}, idsPedido(estadisticas.peoresPedidos(10)));

        Venta[] todas = estadisticas.peoresPedidos(4);
        assertEquals(3000.0 / 220, todas[0].getRendimiento(), 1e-9);
        assertEquals(1500.0 / 85, todas[1].getRendimiento(), 1e-9);
        assertEquals(190.0, todas[2].getRendimiento(), 1e-9);
        assertEquals(2400.0, todas[3].getRendimiento(), 1e-9); // tiempo 0 cuenta como 1
    }

    @Test
    void historialDeLaMasRecienteALaMasAntigua() {
        cargarEscenario();
        Venta[] historial = estadisticas.historialVentas();
        assertEquals(4, historial.length);
        assertEquals("V-P4", historial[0].getIdVenta());
        assertEquals("V-P3", historial[1].getIdVenta());
        assertEquals("V-P2", historial[2].getIdVenta());
        assertEquals("V-P1", historial[3].getIdVenta());
    }

    @Test
    void historialPorRangoDeFechas() {
        cargarEscenario();
        LocalDate dia14 = LocalDate.of(2026, 11, 14);
        assertArrayEquals(new String[]{"P3", "P2", "P1"}, idsPedido(estadisticas.historialVentas(dia14, dia14)));
        assertArrayEquals(new String[]{"P4", "P3", "P2", "P1"},
                idsPedido(estadisticas.historialVentas(dia14, LocalDate.of(2026, 11, 15))));
        assertEquals(0, estadisticas.historialVentas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).length);
    }

    @Test
    void montoTotalVendido() {
        cargarEscenario();
        assertEquals(10700.0, estadisticas.montoTotalVendido());
    }

    // ------------------------------------------------------------------
    // Casos especiales
    // ------------------------------------------------------------------

    @Test
    void sinVentas() {
        assertEquals(0, estadisticas.historialVentas().length);
        assertNull(estadisticas.productoEstrella());
        assertEquals(0, estadisticas.rankingProductos().length);
        assertEquals(0, estadisticas.peoresPedidos(3).length);
        assertEquals(0.0, estadisticas.montoTotalVendido());
    }

    @Test
    void empateEnMontoGanaElDeMasUnidades() {
        LocalDateTime f = LocalDateTime.of(2026, 11, 14, 10, 0);
        repositorio.guardar(venta("P1", "San José", f, 10, d("X", "Producto X", 2, 1000)));  // 2000, 2 u.
        repositorio.guardar(venta("P2", "San José", f, 10, d("Y", "Producto Y", 4, 500)));   // 2000, 4 u.
        assertEquals("Y", estadisticas.productoEstrella().getCodigoProducto());
    }

    @Test
    void empateTotalGanaElCodigoMenor() {
        LocalDateTime f = LocalDateTime.of(2026, 11, 14, 10, 0);
        repositorio.guardar(venta("P1", "San José", f, 10, d("M02", "Mango", 2, 1000)));
        repositorio.guardar(venta("P2", "San José", f, 10, d("B07", "Banano", 2, 1000)));
        assertEquals("B07", estadisticas.productoEstrella().getCodigoProducto());
        assertEquals("M02", estadisticas.rankingProductos()[1].getCodigoProducto());
    }

    @Test
    void empateEnRendimientoVaPrimeroElDeMasTiempoYLuegoElIdMenor() {
        LocalDateTime f = LocalDateTime.of(2026, 11, 14, 10, 0);
        repositorio.guardar(venta("P1", "San José", f, 10, d("A", "A", 1, 1000)));   // 100
        repositorio.guardar(venta("P2", "San José", f, 20, d("A", "A", 1, 2000)));   // 100, más tiempo
        repositorio.guardar(venta("P0", "San José", f, 10, d("A", "A", 1, 1000)));   // 100, mismo tiempo que P1
        assertArrayEquals(new String[]{"P2", "P0", "P1"}, idsPedido(estadisticas.peoresPedidos(3)));
    }

    @Test
    void cantidadInvalidaEnPeoresPedidos() {
        assertThrows(IllegalArgumentException.class, () -> estadisticas.peoresPedidos(0));
        assertThrows(IllegalArgumentException.class, () -> estadisticas.peoresPedidos(-1));
    }

    @Test
    void rangoDeFechasInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> estadisticas.historialVentas(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 14)));
        assertThrows(IllegalArgumentException.class,
                () -> estadisticas.historialVentas(null, LocalDate.of(2026, 11, 14)));
    }

    @Test
    void ignoraVentasDeOtraSede() {
        cargarEscenario();
        repositorio.guardar(venta("P9", "Alajuela", LocalDateTime.of(2026, 11, 16, 9, 0), 10,
                d("Z01", "Caro", 1, 999999)));
        assertEquals(4, estadisticas.historialVentas().length);
        assertEquals("C01", estadisticas.productoEstrella().getCodigoProducto());
        assertEquals(10700.0, estadisticas.montoTotalVendido());

        // La misma sede escrita distinto sí cuenta
        ServicioEstadisticas sinTilde = new ServicioEstadisticas("san jose", repositorio);
        assertEquals(4, sinTilde.historialVentas().length);
    }

    @Test
    void leeVentasActualizadasEnCadaConsulta() {
        assertEquals(0, estadisticas.historialVentas().length);
        cargarEscenario();
        assertEquals(4, estadisticas.historialVentas().length);
    }

    @Test
    void constructorConNulos() {
        assertThrows(IllegalArgumentException.class, () -> new ServicioEstadisticas(null, repositorio));
        assertThrows(IllegalArgumentException.class, () -> new ServicioEstadisticas("  ", repositorio));
        assertThrows(IllegalArgumentException.class, () -> new ServicioEstadisticas("San José", null));
    }
}
