package cr.ac.tec.convitec.pedidos;

import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;
import cr.ac.tec.convitec.servicios.IMensajeria;
import cr.ac.tec.convitec.servicios.IServicioRutas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServicioPedidosTest {

    private CatalogoGlobal catalogo;
    private InventarioLocal inventario;
    private MensajeriaFalsa mensajeria;
    private RutasFalsas rutas;
    private ServicioPedidos servicio;

    @BeforeEach
    void preparar() {
        catalogo = new CatalogoGlobal(new Producto[]{
                new Producto("B01", "Bebida", "", "Bebidas", 1000),
                new Producto("C01", "Café", "", "Bebidas calientes", 1500)
        });
        inventario = new InventarioLocal(catalogo);
        mensajeria = new MensajeriaFalsa();
        rutas = new RutasFalsas();
        servicio = crearServicio();
    }

    @Test
    void confirmaUnPedidoSurtidoSoloLocalmente() {
        inventario.agregarUnidades("B01", 5);

        ResultadoPedido resultado = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 2)
        });

        assertTrue(resultado.isAceptado());
        assertEquals("ALA-0001", resultado.getPedido().getId());
        assertEquals(EstadoPedido.CONFIRMADO, resultado.getPedido().getEstado());
        assertEquals(3, inventario.obtenerDisponible("B01"));
        assertEquals(1, servicio.listarPendientes().length);
        assertEquals(1, mensajeria.eventos.length);
        assertEquals("publicar:B01:3", mensajeria.eventos[0]);
    }

    @Test
    void confirmaLocalPrimeroPublicaStockYLuegoConfirmaSedeRemota() {
        inventario.agregarUnidades("B01", 2);
        catalogo.actualizarStock("LIB", "B01", 4);

        ResultadoPedido resultado = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 5)
        });

        assertTrue(resultado.isAceptado());
        assertEquals(0, inventario.obtenerDisponible("B01"));
        assertArrayEquals(new String[]{"publicar:B01:0", "confirmar:LIB:ALA-0001:B01"},
                mensajeria.eventos);
        assertEquals(2, resultado.getPedido().getDetalles().length);
        assertEquals(5000, resultado.getPedido().getMontoTotal(), 0.001);
    }

    @Test
    void rechazoRemotoLiberaLaReservaRemotaYLaLocalSinConfirmar() {
        inventario.agregarUnidades("B01", 2);
        catalogo.actualizarStock("LIB", "B01", 3);
        mensajeria.respuesta = RespuestaReserva.rechazo("LIB",
                "Limón: stock insuficiente de B01 (hay 0, se piden 3)");

        ResultadoPedido resultado = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 5)
        });

        assertFalse(resultado.isAceptado());
        assertEquals(EstadoPedido.CANCELADO, resultado.getPedido().getEstado());
        assertEquals("Limón: stock insuficiente de B01 (hay 0, se piden 3)", resultado.getMotivo());
        assertEquals(2, inventario.obtenerDisponible("B01"));
        assertArrayEquals(new String[]{"liberar:LIB:ALA-0001:B01"}, mensajeria.eventos);
        assertEquals(0, servicio.listarPendientes().length);
        assertEquals(1, servicio.listarHistorial().length);
    }

    @Test
    void rechazoDeRutaRevierteReservasYUnaExcepcionDeDestinoCancelaConMotivoClaro() {
        inventario.agregarUnidades("B01", 2);
        rutas.hayRuta = false;

        ResultadoPedido sinRuta = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 1)
        });
        assertFalse(sinRuta.isAceptado());
        assertEquals(2, inventario.obtenerDisponible("B01"));

        rutas.lanzarExcepcion = true;
        ResultadoPedido destinoInexistente = servicio.registrarPedido("Desconocido",
                new SolicitudLinea[]{new SolicitudLinea("B01", 1)});
        assertFalse(destinoInexistente.isAceptado());
        assertTrue(destinoInexistente.getMotivo().contains("no existe"));
        assertEquals(2, inventario.obtenerDisponible("B01"));
    }

    @Test
    void falloRemotoIncluidoTimeoutSiempreEnvíaLiberacion() {
        catalogo.actualizarStock("LIB", "B01", 2);
        mensajeria.respuesta = RespuestaReserva.rechazo("LIB", "Limón no respondió a tiempo");

        ResultadoPedido resultado = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 1)
        });

        assertFalse(resultado.isAceptado());
        assertArrayEquals(new String[]{"liberar:LIB:ALA-0001:B01"}, mensajeria.eventos);
        assertFalse(contieneEvento("confirmar:"));
    }

    @Test
    void validaLineasAgrupaDuplicadosYGeneraIdsConsecutivos() {
        inventario.agregarUnidades("B01", 10);

        ResultadoPedido vacio = servicio.registrarPedido("Centro", new SolicitudLinea[0]);
        ResultadoPedido invalido = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 0)
        });
        ResultadoPedido repetido = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 2), new SolicitudLinea("b01", 3)
        });
        ResultadoPedido desconocido = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("X99", 1)
        });

        assertFalse(vacio.isAceptado());
        assertFalse(invalido.isAceptado());
        assertTrue(repetido.isAceptado());
        assertEquals(1, repetido.getPedido().getDetalles().length);
        assertEquals(5, repetido.getPedido().getDetalles()[0].getCantidad());
        assertFalse(desconocido.isAceptado());
        assertEquals("ALA-0004", desconocido.getPedido().getId());
        assertEquals(4, servicio.listarHistorial().length);
    }

    @Test
    void restauracionContinuaIdsYEntregaEsIdempotente() {
        inventario.agregarUnidades("B01", 4);
        ResultadoPedido registrado = servicio.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 1)
        });
        servicio.marcarEntregado(registrado.getPedido().getId(), LocalDateTime.now());
        servicio.marcarEntregado(registrado.getPedido().getId(), LocalDateTime.now());
        assertEquals(0, servicio.listarPendientes().length);
        assertEquals(EstadoPedido.ENTREGADO,
                servicio.buscarPorId(registrado.getPedido().getId()).getEstado());

        ServicioPedidos restaurado = crearServicio();
        restaurado.cargarPedidos(servicio.exportarPedidos());
        inventario.agregarUnidades("B01", 2);
        ResultadoPedido siguiente = restaurado.registrarPedido("Centro", new SolicitudLinea[]{
                new SolicitudLinea("B01", 1)
        });
        assertEquals("ALA-0002", siguiente.getPedido().getId());
        assertNull(restaurado.obtenerDetalle("NO-EXISTE"));
    }

    private ServicioPedidos crearServicio() {
        return new ServicioPedidos("Alajuela", "ALA", inventario, catalogo, mensajeria, rutas);
    }

    private boolean contieneEvento(String prefijo) {
        for (String evento : mensajeria.eventos) {
            if (evento.startsWith(prefijo)) {
                return true;
            }
        }
        return false;
    }

    private static final class MensajeriaFalsa implements IMensajeria {
        private String[] eventos = new String[0];
        private RespuestaReserva respuesta = RespuestaReserva.ok("LIB");

        @Override
        public void iniciar() {
        }

        @Override
        public void cerrar() {
        }

        @Override
        public RespuestaReserva solicitarReserva(String sedeDestino, String idPedido,
                                                 String codigoProducto, int cantidad) {
            return respuesta;
        }

        @Override
        public void confirmarReserva(String sede, String idPedido, String codigoProducto) {
            registrar("confirmar:" + sede + ":" + idPedido + ":" + codigoProducto);
        }

        @Override
        public void liberarReserva(String sede, String idPedido, String codigoProducto) {
            registrar("liberar:" + sede + ":" + idPedido + ":" + codigoProducto);
        }

        @Override
        public void publicarActualizacionStock(String codigoProducto, int cantidadNueva) {
            registrar("publicar:" + codigoProducto + ":" + cantidadNueva);
        }

        private void registrar(String evento) {
            String[] nuevo = new String[eventos.length + 1];
            System.arraycopy(eventos, 0, nuevo, 0, eventos.length);
            nuevo[eventos.length] = evento;
            eventos = nuevo;
        }
    }

    private static final class RutasFalsas implements IServicioRutas {
        private boolean hayRuta = true;
        private boolean lanzarExcepcion;

        @Override
        public boolean existeRuta(String origen, String destino) {
            if (lanzarExcepcion) {
                throw new IllegalArgumentException("destino inexistente");
            }
            return hayRuta;
        }

        @Override
        public ResultadoRuta caminoMasCorto(String origen, String destino) {
            return null;
        }

        @Override
        public void iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida) {
        }

        @Override
        public boolean hayEntregasPendientes() {
            return false;
        }

        @Override
        public ParadaEntrega calcularSiguienteEntrega() {
            return null;
        }

        @Override
        public RecorridoEntrega obtenerRecorrido() {
            return null;
        }

        @Override
        public RecorridoEntrega planificarRecorridoCompleto(SolicitudEntrega[] pendientes,
                                                            String horaSalida) {
            return null;
        }
    }
}
