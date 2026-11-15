package cr.ac.tec.convitec.modelo;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VentaTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 14, 11, 20);

    private static DetalleVenta[] detallesDeEjemplo() {
        return new DetalleVenta[]{
                new DetalleVenta("C01", "Café", 2, 1500),   // 3000
                new DetalleVenta("P05", "Pan", 1, 1000)     // 1000
        };
    }

    private static Venta venta(DetalleVenta[] detalles, int tiempo) {
        return new Venta("V-P1", "P1", "San José", "Heredia", FECHA, "11:00", detalles, tiempo);
    }

    @Test
    void subtotalesYMontoTotal() {
        DetalleVenta[] d = detallesDeEjemplo();
        assertEquals(3000.0, d[0].getSubtotal());
        assertEquals(1000.0, d[1].getSubtotal());

        Venta v = venta(d, 20);
        assertEquals(4000.0, v.getMontoTotal());
        assertEquals(2, v.getCantidadDetalles());
        assertEquals("San José", v.getSede());
        assertEquals(FECHA, v.getFechaHora());
    }

    @Test
    void rendimientoEsMontoEntreTiempo() {
        assertEquals(200.0, venta(detallesDeEjemplo(), 20).getRendimiento());
    }

    @Test
    void tiempoCeroSeTrataComoUnMinuto() {
        Venta v = venta(detallesDeEjemplo(), 0);
        assertEquals(0, v.getTiempoTraslado());
        assertEquals(4000.0, v.getRendimiento());
    }

    @Test
    void detallesVaciosNulosORepetidosLanzanError() {
        assertThrows(IllegalArgumentException.class, () -> venta(new DetalleVenta[0], 10));
        assertThrows(IllegalArgumentException.class, () -> venta(null, 10));
        assertThrows(IllegalArgumentException.class, () -> venta(new DetalleVenta[]{null}, 10));
        assertThrows(IllegalArgumentException.class, () -> venta(new DetalleVenta[]{
                new DetalleVenta("C01", "Café", 1, 100), new DetalleVenta("c01", "Café", 2, 100)}, 10));
    }

    @Test
    void detalleInvalidoLanzaError() {
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta("C01", "Café", 0, 1500));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta("C01", "Café", -2, 1500));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta("C01", "Café", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta("C01", "Café", 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta(" ", "Café", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> new DetalleVenta("C01", null, 1, 100));
        assertEquals(0.0, new DetalleVenta("R01", "Regalía", 1, 0).getSubtotal()); // precio 0 sí se permite
    }

    @Test
    void ventaInvalidaLanzaError() {
        DetalleVenta[] d = detallesDeEjemplo();
        assertThrows(IllegalArgumentException.class, () -> venta(d, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Venta("V-P1", "P1", "San José", "Heredia", null, "11:00", d, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new Venta("V-P1", "P1", "San José", "Heredia", FECHA, "12:00", d, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new Venta("V-P1", "", "San José", "Heredia", FECHA, "11:00", d, 10));
    }

    @Test
    void gettersDevuelvenCopias() {
        DetalleVenta[] original = detallesDeEjemplo();
        Venta v = venta(original, 20);

        original[0] = new DetalleVenta("X99", "Otro", 100, 100); // cambiar el arreglo original
        assertEquals("C01", v.getDetalles()[0].getCodigoProducto());

        DetalleVenta[] copia = v.getDetalles();
        copia[0] = null;                                          // cambiar la copia
        assertNotNull(v.getDetalles()[0]);
        assertEquals(4000.0, v.getMontoTotal());
    }

    @Test
    void toStringLegible() {
        String texto = venta(detallesDeEjemplo(), 20).toString();
        assertTrue(texto.contains("V-P1"));
        assertTrue(texto.contains("Heredia"));
        assertTrue(texto.contains("Café"));
    }
}
