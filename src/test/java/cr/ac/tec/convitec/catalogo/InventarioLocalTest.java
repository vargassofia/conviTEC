package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InventarioLocalTest {

    private CatalogoGlobal catalogo;
    private InventarioLocal inventario;

    @BeforeEach
    void preparar() {
        catalogo = new CatalogoGlobal(new Producto[]{
                new Producto("B01", "Bebida", "", "Bebidas", 1000),
                new Producto("C01", "Café", "", "Bebidas calientes", 1500),
                new Producto("S01", "Sándwich", "", "Comidas", 2500)
        });
        inventario = new InventarioLocal(catalogo);
        inventario.agregarUnidades("B01", 5);
        inventario.agregarUnidades("C01", 10);
    }

    @Test
    void reservaCorrectamenteYRepetirLaMismaEsIdempotente() {
        assertTrue(inventario.reservar("P7", "B01", 2).isExito());
        assertTrue(inventario.reservar("P7", "B01", 4).isExito());
        assertEquals(3, inventario.obtenerDisponible("B01"));
        assertEquals(2, inventario.consultar("B01").getCantidadReservada());
        assertEquals(1, inventario.getCantidadReservas());
    }

    @Test
    void reservaSinStockDevuelveMotivoComoLaDemo() {
        RespuestaReserva respuesta = inventario.reservar("P7", "B01", 6);

        assertFalse(respuesta.isExito());
        assertEquals("stock insuficiente de B01 (hay 5, se piden 6)", respuesta.getMotivo());
        assertEquals(5, inventario.obtenerDisponible("B01"));
    }

    @Test
    void rechazaReservaDeProductoInexistenteYCantidadInvalida() {
        assertEquals("el producto X99 no existe",
                inventario.reservar("P7", "X99", 1).getMotivo());
        assertFalse(inventario.reservar("P7", "B01", 0).isExito());
        assertFalse(inventario.reservar("P7", "B01", -1).isExito());
        assertFalse(inventario.reservar(null, "B01", 1).isExito());
        assertFalse(inventario.reservar("P7", null, 1).isExito());
    }

    @Test
    void confirmarDescuentaUnaVezYDevuelveStockNuevo() {
        inventario.reservar("P7", "B01", 2);

        assertEquals(3, inventario.confirmar("P7", "B01"));
        assertEquals(3, inventario.confirmar("P7", "B01"));
        assertEquals(0, inventario.consultar("B01").getCantidadReservada());
        assertEquals(-1, inventario.confirmar("NO", "X99"));
    }

    @Test
    void liberarDevuelveUnidadesUnaSolaVez() {
        inventario.reservar("P7", "B01", 2);

        inventario.liberar("P7", "B01");
        inventario.liberar("P7", "B01");
        inventario.liberar("NO", "B01");

        assertEquals(5, inventario.obtenerDisponible("B01"));
        assertEquals(0, inventario.getCantidadReservas());
    }

    @Test
    void agregarAProductoNuevoDelCatalogoYExistente() {
        assertEquals(1, inventario.agregarUnidades("S01", 1));
        assertEquals(6, inventario.agregarUnidades("B01", 1));
        assertThrows(IllegalArgumentException.class, () -> inventario.agregarUnidades("X99", 1));
        assertThrows(IllegalArgumentException.class, () -> inventario.agregarUnidades("B01", 0));
        assertThrows(IllegalArgumentException.class, () -> inventario.agregarUnidades(null, 1));
    }

    @Test
    void descontarValidaDisponibleYEntradas() {
        assertEquals(7, inventario.descontar("C01", 3));
        assertThrows(IllegalArgumentException.class, () -> inventario.descontar("B01", 6));
        assertThrows(IllegalArgumentException.class, () -> inventario.descontar("B01", 0));
        assertThrows(IllegalArgumentException.class, () -> inventario.descontar("X99", 1));
    }

    @Test
    void consultaYListadoLocalOrdenadoConPrecio() {
        ExistenciaInventario[] listado = inventario.listarExistencias();

        assertEquals(2, listado.length);
        assertEquals("B01", listado[0].getCodigoProducto());
        assertEquals(1000, listado[0].getPrecio());
        assertEquals("C01", listado[1].getCodigoProducto());
        assertNull(inventario.consultar("S01"));
        assertThrows(IllegalArgumentException.class, () -> inventario.consultar(null));
        assertEquals(-1, inventario.obtenerDisponible("X99"));
    }

    @Test
    void exportaYCargaExistenciasYReservas() {
        inventario.reservar("P7", "B01", 2);
        Existencia[] existencias = inventario.exportarExistencias();
        ReservaPersistida[] reservas = inventario.exportarReservas();
        InventarioLocal restaurado = new InventarioLocal(catalogo);

        restaurado.cargarExistencias(existencias);
        restaurado.cargarReservas(reservas);

        assertEquals(3, restaurado.obtenerDisponible("B01"));
        assertEquals(2, restaurado.consultar("B01").getCantidadReservada());
        assertTrue(restaurado.reservar("P7", "B01", 5).isExito());
        assertEquals(3, restaurado.confirmar("P7", "B01"));
    }

    @Test
    void cargaRechazaArreglosInvalidosSinCambiarExistencias() {
        assertThrows(IllegalArgumentException.class, () -> inventario.cargarExistencias(null));
        assertThrows(IllegalArgumentException.class,
                () -> inventario.cargarExistencias(new Existencia[]{null}));
        assertThrows(IllegalArgumentException.class,
                () -> inventario.cargarExistencias(new Existencia[]{
                        new Existencia("B01", 2), new Existencia("b01", 3)}));
        assertEquals(5, inventario.obtenerDisponible("B01"));
    }

    @Test
    void concurrenciaNoPermiteReservarMasStockDelDisponible() throws InterruptedException {
        int cantidadHilos = 20;
        CountDownLatch listos = new CountDownLatch(cantidadHilos);
        CountDownLatch iniciar = new CountDownLatch(1);
        AtomicInteger exitosas = new AtomicInteger();
        Thread[] hilos = new Thread[cantidadHilos];

        for (int i = 0; i < cantidadHilos; i++) {
            final int numero = i;
            hilos[i] = new Thread(() -> {
                listos.countDown();
                try {
                    iniciar.await();
                    if (inventario.reservar("PED-" + numero, "B01", 1).isExito()) {
                        exitosas.incrementAndGet();
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            });
            hilos[i].start();
        }

        listos.await();
        iniciar.countDown();
        for (Thread hilo : hilos) {
            hilo.join();
        }

        assertEquals(5, exitosas.get());
        assertEquals(0, inventario.obtenerDisponible("B01"));
        assertEquals(5, inventario.consultar("B01").getCantidadReservada());
    }
}
