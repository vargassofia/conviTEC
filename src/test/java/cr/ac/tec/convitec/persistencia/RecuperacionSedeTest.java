package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.DetallePedido;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;
import cr.ac.tec.convitec.servicios.IMensajeria;
import cr.ac.tec.convitec.servicios.IServicioRutas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecuperacionSedeTest {

    @TempDir
    Path carpeta;

    @Test
    void cancelaPedidoEnProcesoYLiberaReservaLocalAlReiniciar() {
        PersistenciaSede persistencia = new PersistenciaSede(carpeta);
        Producto producto = new Producto("C01", "Café", "", "Bebidas", 1500);
        CatalogoGlobal catalogo = new CatalogoGlobal(new Producto[]{producto});
        InventarioLocal inventario = new InventarioLocal(catalogo);
        inventario.agregarUnidades("C01", 5);
        inventario.reservar("ALA-0001", "C01", 2);
        persistencia.guardarCatalogo(new Producto[]{producto});
        persistencia.guardarInventario(inventario);
        persistencia.guardarPedidos(new Pedido[]{pedido("C01", "ALA")}, 1);

        ResultadoRecuperacion resultado = new RecuperacionSede(persistencia)
                .recuperar("Alajuela", "ALA", new MensajeriaFalsa(), new RutasFalsas());

        assertEquals(EstadoPedido.CANCELADO,
                resultado.getServicioPedidos().buscarPorId("ALA-0001").getEstado());
        assertEquals("La sede se reinició antes de completar el pedido",
                resultado.getServicioPedidos().buscarPorId("ALA-0001").getMotivoCancelacion());
        assertEquals(5, resultado.getInventario().obtenerDisponible("C01"));
        assertEquals(0, resultado.getInventario().getCantidadReservas());
        assertEquals(0, resultado.getLiberacionesPendientes().length);

        EstadoSede alReabrir = persistencia.cargarTodo();
        assertEquals(EstadoPedido.CANCELADO, alReabrir.getPedidos()[0].getEstado());
    }

    @Test
    void devuelveLiberacionesRemotasParaEnviarlasTrasConectarMensajeria() {
        PersistenciaSede persistencia = new PersistenciaSede(carpeta);
        Producto producto = new Producto("B01", "Bebida", "", "Bebidas", 1000);
        persistencia.guardarCatalogo(new Producto[]{producto});
        persistencia.guardarPedidos(new Pedido[]{pedido("B01", "LIB")}, 1);

        ResultadoRecuperacion resultado = new RecuperacionSede(persistencia)
                .recuperar("Alajuela", "ALA", new MensajeriaFalsa(), new RutasFalsas());

        LiberacionPendiente[] pendientes = resultado.getLiberacionesPendientes();
        assertEquals(1, pendientes.length);
        assertEquals("LIB", pendientes[0].getCodigoSede());
        assertEquals("ALA-0001", pendientes[0].getIdPedido());
        assertEquals("B01", pendientes[0].getCodigoProducto());
    }

    private Pedido pedido(String codigo, String sedeSurtidora) {
        return new Pedido("ALA-0001", "ALA", "Heredia", LocalDateTime.of(2026, 11, 26, 10, 0),
                EstadoPedido.EN_PROCESO, null,
                new DetallePedido[]{new DetallePedido(codigo, codigo, 2, 1000, sedeSurtidora)},
                new String[]{"ALA", sedeSurtidora});
    }

    private static final class MensajeriaFalsa implements IMensajeria {
        @Override public void iniciar() { }
        @Override public void cerrar() { }
        @Override public RespuestaReserva solicitarReserva(String sede, String id, String codigo, int cantidad) {
            return RespuestaReserva.ok(sede);
        }
        @Override public void confirmarReserva(String sede, String id, String codigo) { }
        @Override public void liberarReserva(String sede, String id, String codigo) { }
        @Override public void publicarActualizacionStock(String codigo, int cantidad) { }
    }

    private static final class RutasFalsas implements IServicioRutas {
        @Override public boolean existeRuta(String origen, String destino) { return true; }
        @Override public cr.ac.tec.convitec.grafo.ResultadoRuta caminoMasCorto(String origen, String destino) {
            return null;
        }
        @Override public void iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida) { }
        @Override public boolean hayEntregasPendientes() { return false; }
        @Override public ParadaEntrega calcularSiguienteEntrega() { return null; }
        @Override public RecorridoEntrega obtenerRecorrido() { return null; }
        @Override public RecorridoEntrega planificarRecorridoCompleto(SolicitudEntrega[] pendientes,
                                                                      String horaSalida) {
            return null;
        }
    }
}
