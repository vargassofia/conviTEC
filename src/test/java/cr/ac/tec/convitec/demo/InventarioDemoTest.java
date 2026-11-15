package cr.ac.tec.convitec.demo;

import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventarioDemoTest {

    private InventarioDemo limon;

    @BeforeEach
    void preparar() {
        limon = InventarioDemo.paraSede("LIM"); // B01 Bebida: 5 unidades
    }

    @Test
    void reservarConStock() {
        RespuestaReserva r = limon.reservar("P7", "B01", 2);
        assertTrue(r.isExito());
        assertEquals(3, limon.getStock("B01"));
        assertEquals(2, limon.getReservado("B01"));
    }

    @Test
    void reservarSinStockOProductoInexistente() {
        RespuestaReserva r = limon.reservar("P7", "B01", 6);
        assertFalse(r.isExito());
        assertEquals("stock insuficiente de B01 (hay 5, se piden 6)", r.getMotivo());
        assertEquals(5, limon.getStock("B01"));

        assertFalse(limon.reservar("P7", "X99", 1).isExito());
        assertFalse(limon.reservar("P7", "B01", 0).isExito());
        assertFalse(limon.reservar(null, "B01", 1).isExito());
    }

    @Test
    void reservaDuplicadaSeRechaza() {
        assertTrue(limon.reservar("P7", "B01", 1).isExito());
        assertFalse(limon.reservar("P7", "B01", 1).isExito());
        assertTrue(limon.reservar("P8", "B01", 1).isExito()); // otro pedido sí puede
    }

    @Test
    void confirmarDescuentaYDevuelveElStockNuevo() {
        limon.reservar("P7", "B01", 2);
        assertEquals(3, limon.confirmar("P7", "B01"));
        assertEquals(0, limon.getReservado("B01"));
        assertEquals(3, limon.getStock("B01"));
    }

    @Test
    void liberarDevuelveLasUnidades() {
        limon.reservar("P7", "B01", 2);
        limon.liberar("P7", "B01");
        assertEquals(5, limon.getStock("B01"));
        assertEquals(0, limon.getCantidadReservas());
    }

    @Test
    void confirmarYLiberarDosVecesNoFallan() {
        limon.reservar("P7", "B01", 2);
        assertEquals(3, limon.confirmar("P7", "B01"));
        assertEquals(3, limon.confirmar("P7", "B01"));   // no descuenta otra vez
        limon.liberar("P7", "B01");                      // ya confirmada: no devuelve nada
        assertEquals(3, limon.getStock("B01"));

        limon.reservar("P8", "B01", 1);
        limon.liberar("P8", "B01");
        limon.liberar("P8", "B01");                      // segunda vez: nada
        assertEquals(3, limon.getStock("B01"));
        assertDoesNotThrow(() -> limon.liberar("NO", "EXISTE"));
        assertEquals(-1, limon.confirmar("NO", "EXISTE")); // producto inexistente: -1, sin excepción
    }

    @Test
    void agregarUnidadesYListar() {
        assertEquals(8, limon.agregarUnidades("B01", 3));
        assertThrows(IllegalArgumentException.class, () -> limon.agregarUnidades("X99", 1));
        assertThrows(IllegalArgumentException.class, () -> limon.agregarUnidades("B01", 0));
        String[] lineas = limon.listarProductos();
        assertEquals(3, lineas.length);
        assertTrue(lineas[0].contains("B01") && lineas[0].contains("Bebida"));
    }

    @Test
    void productosDistintosPorSede() {
        assertEquals(-1, InventarioDemo.paraSede("ALA").getStock("B01")); // B01 solo existe en LIM
        assertTrue(InventarioDemo.paraSede("ALA").getStock("C01") > 0);
        assertThrows(IllegalArgumentException.class, () -> InventarioDemo.paraSede("XYZ"));
    }

    @Test
    void catalogoGlobalDemo() {
        CatalogoGlobalDemo c = new CatalogoGlobalDemo();
        assertEquals(0, c.listar().length);
        for (int i = 0; i < 20; i++) {           // más que la capacidad inicial
            c.actualizarStock("LIM", "P" + i, i);
        }
        c.actualizarStock("LIM", "P3", 99);       // actualiza, no duplica
        assertEquals(20, c.getCantidadRegistros());
        assertEquals(99, c.getStock("LIM", "P3"));
        assertEquals(-1, c.getStock("SJO", "P3"));
    }
}
