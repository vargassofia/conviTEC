package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.servicios.IMensajeria;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServicioInventarioTest {

    @Test
    void consultaYModificacionesPublicanElStockNuevo() {
        CatalogoGlobal catalogo = new CatalogoGlobal(new Producto[]{
                new Producto("B01", "Bebida", "", "Bebidas", 1000)
        });
        InventarioLocal inventario = new InventarioLocal(catalogo);
        inventario.agregarUnidades("B01", 5);
        MensajeriaFalsa mensajeria = new MensajeriaFalsa();
        ServicioInventario servicio = new ServicioInventario(inventario, catalogo, mensajeria);

        assertEquals(1, servicio.consultarInventarioLocal().length);
        assertEquals(1, servicio.consultarCatalogoGlobal().length);
        assertEquals(7, servicio.agregarUnidades("B01", 2));
        assertEquals(7, mensajeria.ultimoStock);
        assertEquals("B01", mensajeria.ultimoCodigo);
        assertEquals(4, servicio.descontar("B01", 3));
        assertEquals(4, mensajeria.ultimoStock);
    }

    @Test
    void errorDePublicacionSeInformaYConservaElCambioLocal() {
        CatalogoGlobal catalogo = new CatalogoGlobal(new Producto[]{
                new Producto("B01", "Bebida", "", "Bebidas", 1000)
        });
        InventarioLocal inventario = new InventarioLocal(catalogo);
        inventario.agregarUnidades("B01", 5);
        MensajeriaFalsa mensajeria = new MensajeriaFalsa();
        mensajeria.fallarPublicacion = true;
        ServicioInventario servicio = new ServicioInventario(inventario, catalogo, mensajeria);

        ServicioInventarioException error = assertThrows(ServicioInventarioException.class,
                () -> servicio.descontar("B01", 2));

        assertTrue(error.getMessage().contains("stock local se actualizó"));
        assertEquals(3, inventario.obtenerDisponible("B01"));
    }

    @Test
    void rechazaDependenciasNulas() {
        CatalogoGlobal catalogo = new CatalogoGlobal();
        InventarioLocal inventario = new InventarioLocal(catalogo);

        assertThrows(IllegalArgumentException.class,
                () -> new ServicioInventario(null, catalogo, new MensajeriaFalsa()));
        assertThrows(IllegalArgumentException.class,
                () -> new ServicioInventario(inventario, null, new MensajeriaFalsa()));
        assertThrows(IllegalArgumentException.class,
                () -> new ServicioInventario(inventario, catalogo, null));
    }

    private static final class MensajeriaFalsa implements IMensajeria {
        private String ultimoCodigo;
        private int ultimoStock;
        private boolean fallarPublicacion;

        @Override
        public void iniciar() {
        }

        @Override
        public void cerrar() {
        }

        @Override
        public RespuestaReserva solicitarReserva(String sedeDestino, String idPedido,
                                                 String codigoProducto, int cantidad) {
            return RespuestaReserva.ok(sedeDestino);
        }

        @Override
        public void confirmarReserva(String sedeDestino, String idPedido, String codigoProducto) {
        }

        @Override
        public void liberarReserva(String sedeDestino, String idPedido, String codigoProducto) {
        }

        @Override
        public void publicarActualizacionStock(String codigoProducto, int cantidadNueva) {
            if (fallarPublicacion) {
                throw new IllegalStateException("Broker no disponible");
            }
            ultimoCodigo = codigoProducto;
            ultimoStock = cantidadNueva;
        }
    }
}
