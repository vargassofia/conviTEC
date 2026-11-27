package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.catalogo.ReservaPersistida;
import cr.ac.tec.convitec.modelo.DetallePedido;
import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.modelo.Venta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PersistenciaSedeTest {

    @TempDir
    Path carpeta;

    @Test
    void primerArranqueCargaCatalogoInicialYRestoVacio() {
        EstadoSede estado = new PersistenciaSede(carpeta).cargarTodo();

        assertTrue(estado.getProductos().length > 0);
        assertEquals(0, estado.getExistencias().length);
        assertEquals(0, estado.getReservas().length);
        assertEquals(0, estado.getPedidos().length);
        assertEquals(0, estado.getVentas().length);
        assertTrue(carpeta.resolve("productos.json").toFile().exists());
    }

    @Test
    void exportaYCargaInventarioReservasCatalogoPedidosVentasYStock() {
        Producto producto = new Producto("C01", "Café", "", "Bebidas", 1500);
        CatalogoGlobal catalogo = new CatalogoGlobal(new Producto[]{producto});
        catalogo.actualizarStock("LIB", "C01", 8);
        InventarioLocal inventario = new InventarioLocal(catalogo);
        inventario.agregarUnidades("C01", 5);
        inventario.reservar("ALA-0001", "C01", 2);

        DetallePedido detallePedido = new DetallePedido("C01", "Café", 1, 1500, "ALA");
        Pedido pedido = new Pedido("ALA-0001", "ALA", "Heredia",
                LocalDateTime.parse("2026-11-26T10:00:00"), EstadoPedido.CONFIRMADO,
                null, new DetallePedido[]{detallePedido}, new String[]{"ALA"});
        DetalleVenta detalleVenta = new DetalleVenta("C01", "Café", 1, 1500);
        Venta venta = new Venta("V-ALA-0001", "ALA-0001", "Alajuela", "Heredia",
                LocalDateTime.parse("2026-11-26T11:00:00"), "11:00",
                new DetalleVenta[]{detalleVenta}, 20);
        PersistenciaSede persistencia = new PersistenciaSede(carpeta);
        persistencia.guardarCatalogo(new Producto[]{producto});
        persistencia.guardarInventario(inventario);
        persistencia.guardarStockConocido(catalogo);
        persistencia.guardarPedidos(new Pedido[]{pedido}, 1);
        persistencia.guardarVentas(new Venta[]{venta});

        EstadoSede estado = persistencia.cargarTodo();
        assertEquals(producto, estado.getProductos()[0]);
        assertEquals(new Existencia("C01", 3, 2), estado.getExistencias()[0]);
        assertEquals(new ReservaPersistida("ALA-0001", "C01", 2).getCantidad(),
                estado.getReservas()[0].getCantidad());
        assertEquals(EstadoPedido.CONFIRMADO, estado.getPedidos()[0].getEstado());
        assertEquals("2026-11-26T11:00", estado.getVentas()[0].getFechaHora().toString());
        assertEquals(8, estado.getStocksConocidos()[0].stock);
        assertEquals(1, estado.getContadorIds());
    }

    @Test
    void descartaRegistrosIndividualesInvalidosSinCancelarTodaLaCarga() {
        PersistenciaSede persistencia = new PersistenciaSede(carpeta);
        persistencia.guardarCatalogo(new Producto[]{
                new Producto("C01", "Café", "", "Bebidas", 1500)
        });
        persistencia.guardarExistencias(new Existencia[]{
                new Existencia("C01", 3),
                new Existencia("X99", 5)
        });
        EstadoSede estado = persistencia.cargarTodo();
        assertEquals(1, estado.getExistencias().length);
        assertTrue(estado.getResultadoCarga().tieneAvisos());
    }

    @Test
    void reportaVersionDeFormatoDesconocida() throws IOException {
        PersistenciaSede persistencia = new PersistenciaSede(carpeta);
        persistencia.guardarCatalogo(new Producto[]{
                new Producto("C01", "Café", "", "Bebidas", 1500)
        });
        Files.writeString(carpeta.resolve("inventario.json"), "[{\"version\":99}]");

        EstadoSede estado = persistencia.cargarTodo();

        assertEquals(0, estado.getExistencias().length);
        boolean reportoVersion = false;
        for (String aviso : estado.getResultadoCarga().getAvisos()) {
            if (aviso.contains("desconocida")) {
                reportoVersion = true;
                break;
            }
        }
        assertTrue(reportoVersion);
    }

    @Test
    void carpetasSeparadasNoCompartenInventario() {
        Path ala = carpeta.resolve("ALA");
        Path lim = carpeta.resolve("LIM");
        PersistenciaSede persistenciaAla = new PersistenciaSede(ala);
        PersistenciaSede persistenciaLim = new PersistenciaSede(lim);
        persistenciaAla.guardarCatalogo(new Producto[]{new Producto("C01", "Café", "", "Bebidas", 1500)});
        persistenciaLim.guardarCatalogo(new Producto[]{new Producto("C01", "Café", "", "Bebidas", 1500)});
        persistenciaAla.guardarExistencias(new Existencia[]{new Existencia("C01", 4)});
        assertEquals(1, persistenciaAla.cargarTodo().getExistencias().length);
        assertEquals(0, persistenciaLim.cargarTodo().getExistencias().length);
    }
}
