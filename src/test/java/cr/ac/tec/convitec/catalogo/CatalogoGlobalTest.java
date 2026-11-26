package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.modelo.Producto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoGlobalTest {

    @Test
    void catalogoVacioDevuelveArregloVacio() {
        CatalogoGlobal catalogo = new CatalogoGlobal();

        assertEquals(0, catalogo.listarCatalogo().length);
        assertEquals(0, catalogo.disponibilidadDe("B01").length);
        assertNull(catalogo.obtenerProducto("B01"));
    }

    @Test
    void actualizaStockPorSedeYNoDuplicaClaves() {
        CatalogoGlobal catalogo = catalogoConProductos();

        catalogo.actualizarStock("LIM", "B01", 5);
        catalogo.actualizarStock("LIM", "B01", 3);
        catalogo.actualizarStock("ALA", "B01", 8);

        DisponibilidadSede[] disponibilidad = catalogo.disponibilidadDe("b01");
        assertEquals(2, disponibilidad.length);
        assertEquals("ALA", disponibilidad[0].getCodigoSede());
        assertEquals(8, disponibilidad[0].getCantidadDisponible());
        assertEquals("LIM", disponibilidad[1].getCodigoSede());
        assertEquals(3, disponibilidad[1].getCantidadDisponible());
    }

    @Test
    void descartaMensajesInvalidosYCuenta() {
        CatalogoGlobal catalogo = catalogoConProductos();

        catalogo.actualizarStock(null, "B01", 1);
        catalogo.actualizarStock("ALA", " ", 1);
        catalogo.actualizarStock("ALA", "B01", -1);
        catalogo.actualizarStock("XYZ", "B01", 1);

        assertEquals(4, catalogo.getMensajesDescartados());
        assertEquals(0, catalogo.disponibilidadDe("B01").length);
    }

    @Test
    void catalogoOrdenadoIncluyeProductosSinExistenciaLocalYDisponibilidad() {
        CatalogoGlobal catalogo = catalogoConProductos();
        catalogo.actualizarStock("LIM", "B01", 5);
        catalogo.actualizarStock("SJO", "B01", 2);

        ProductoCatalogo[] listado = catalogo.listarCatalogo();
        assertEquals(2, listado.length);
        assertEquals("B01", listado[0].getProducto().getCodigo());
        assertEquals(2, listado[0].getDisponibilidades().length);
        assertEquals("C01", listado[1].getProducto().getCodigo());
        assertEquals(0, listado[1].getDisponibilidades().length);
    }

    @Test
    void stockDeCodigoAunNoRegistradoSeAlmacena() {
        CatalogoGlobal catalogo = new CatalogoGlobal();

        catalogo.actualizarStock("LIB", "X99", 7);

        assertEquals(7, catalogo.disponibilidadDe("X99")[0].getCantidadDisponible());
        assertEquals(0, catalogo.listarCatalogo().length);
    }

    private CatalogoGlobal catalogoConProductos() {
        return new CatalogoGlobal(new Producto[]{
                new Producto("C01", "Café", "", "Bebidas", 1500),
                new Producto("B01", "Bebida", "", "Bebidas", 1000)
        });
    }
}
