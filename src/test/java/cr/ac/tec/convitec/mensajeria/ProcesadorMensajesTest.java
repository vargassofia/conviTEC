package cr.ac.tec.convitec.mensajeria;

import cr.ac.tec.convitec.servicios.ICatalogoGlobal;
import cr.ac.tec.convitec.servicios.IInventarioLocal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcesadorMensajesTest {

    // ------------------------------------------------------------------
    // Falsos hechos con arreglos (también los usa MensajeriaRabbitIntegracionTest)
    // ------------------------------------------------------------------

    /** Inventario falso: productos con stock y reservas por (pedido, producto). */
    static class InventarioFalso implements IInventarioLocal {
        private final String[] codigos;
        private final int[] stock;
        private final String[] reservaPedido = new String[50];
        private final String[] reservaProducto = new String[50];
        private final int[] reservaCantidad = new int[50];
        private int reservas = 0;
        int liberaciones = 0;
        int confirmaciones = 0;
        boolean fallar = false;

        InventarioFalso(String[] codigos, int[] stock) {
            this.codigos = codigos;
            this.stock = stock;
        }

        @Override
        public synchronized RespuestaReserva reservar(String idPedido, String codigoProducto, int cantidad) {
            if (fallar) {
                throw new IllegalStateException("archivo de inventario dañado");
            }
            int p = producto(codigoProducto);
            if (p == -1) {
                return RespuestaReserva.rechazo(null, "el producto " + codigoProducto + " no existe");
            }
            if (stock[p] < cantidad) {
                return RespuestaReserva.rechazo(null, "stock insuficiente de " + codigoProducto
                        + " (hay " + stock[p] + ", se piden " + cantidad + ")");
            }
            stock[p] -= cantidad; // apartado: ya no está disponible
            reservaPedido[reservas] = idPedido;
            reservaProducto[reservas] = codigoProducto;
            reservaCantidad[reservas] = cantidad;
            reservas++;
            return RespuestaReserva.ok(null);
        }

        @Override
        public synchronized int confirmar(String idPedido, String codigoProducto) {
            confirmaciones++;
            quitarReserva(idPedido, codigoProducto); // lo apartado ya salió del stock: solo se olvida la reserva
            return stockDe(codigoProducto);
        }

        @Override
        public synchronized void liberar(String idPedido, String codigoProducto) {
            liberaciones++;
            int cantidad = quitarReserva(idPedido, codigoProducto);
            int p = producto(codigoProducto);
            if (p != -1) {
                stock[p] += cantidad; // idempotente: si no había reserva, cantidad = 0
            }
        }

        synchronized int stockDe(String codigo) {
            int p = producto(codigo);
            return p == -1 ? 0 : stock[p];
        }

        synchronized int getLiberaciones() {
            return liberaciones;
        }

        private int quitarReserva(String idPedido, String codigoProducto) {
            for (int i = 0; i < reservas; i++) {
                if (reservaPedido[i].equals(idPedido) && reservaProducto[i].equals(codigoProducto)) {
                    int cantidad = reservaCantidad[i];
                    reservas--;
                    reservaPedido[i] = reservaPedido[reservas];
                    reservaProducto[i] = reservaProducto[reservas];
                    reservaCantidad[i] = reservaCantidad[reservas];
                    return cantidad;
                }
            }
            return 0;
        }

        private int producto(String codigo) {
            for (int i = 0; i < codigos.length; i++) {
                if (codigos[i].equals(codigo)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** Catálogo falso: guarda la última actualización recibida. */
    static class CatalogoFalso implements ICatalogoGlobal {
        int actualizaciones = 0;
        String ultimaSede;
        String ultimoProducto;
        int ultimaCantidad = -1;

        @Override
        public synchronized void actualizarStock(String codigoSede, String codigoProducto, int cantidadNueva) {
            actualizaciones++;
            ultimaSede = codigoSede;
            ultimoProducto = codigoProducto;
            ultimaCantidad = cantidadNueva;
        }

        synchronized int getActualizaciones() {
            return actualizaciones;
        }
    }

    // ------------------------------------------------------------------
    // Pruebas (procesador de la sede LIM)
    // ------------------------------------------------------------------

    private InventarioFalso inventario;
    private CatalogoFalso catalogo;
    private ProcesadorMensajes procesador;

    @BeforeEach
    void preparar() {
        inventario = new InventarioFalso(new String[]{"B01", "B02"}, new int[]{5, 1});
        catalogo = new CatalogoFalso();
        procesador = new ProcesadorMensajes("LIM", inventario, catalogo);
    }

    @Test
    void reservaConStock() {
        Mensaje solicitud = Mensaje.solicitarReserva("ALA", "LIM", "P7", "B01", 2);
        Mensaje r = procesador.procesar(solicitud);
        assertEquals(TipoMensaje.RESPUESTA_RESERVA, r.getTipo());
        assertTrue(r.isExito());
        assertEquals(solicitud.getCorrelationId(), r.getCorrelationId());
        assertEquals("ALA", r.getSedeDestino());
        assertEquals(3, inventario.stockDe("B01"));
    }

    @Test
    void reservaRechazadaPorFaltaDeStock() {
        Mensaje r = procesador.procesar(Mensaje.solicitarReserva("ALA", "LIM", "P7", "B02", 2));
        assertFalse(r.isExito());
        assertEquals("stock insuficiente de B02 (hay 1, se piden 2)", r.getMotivo());
        assertEquals(1, inventario.stockDe("B02"));
    }

    @Test
    void confirmarDevuelveLaActualizacionDeStock() {
        procesador.procesar(Mensaje.solicitarReserva("ALA", "LIM", "P7", "B01", 2));
        Mensaje a = procesador.procesar(Mensaje.confirmarReserva("ALA", "LIM", "P7", "B01"));
        assertEquals(TipoMensaje.ACTUALIZACION_STOCK, a.getTipo());
        assertEquals("LIM", a.getSedeOrigen());
        assertNull(a.getSedeDestino());
        assertEquals("B01", a.getCodigoProducto());
        assertEquals(3, a.getCantidad());
        assertEquals(1, inventario.confirmaciones);
    }

    @Test
    void liberarLlamaAlInventario() {
        procesador.procesar(Mensaje.solicitarReserva("ALA", "LIM", "P7", "B01", 2));
        assertNull(procesador.procesar(Mensaje.liberarReserva("ALA", "LIM", "P7", "B01")));
        assertEquals(1, inventario.liberaciones);
        assertEquals(5, inventario.stockDe("B01"));

        // Idempotente: liberar otra vez no cambia nada
        procesador.procesar(Mensaje.liberarReserva("ALA", "LIM", "P7", "B01"));
        assertEquals(5, inventario.stockDe("B01"));
    }

    @Test
    void actualizacionPropiaSeIgnoraYLaDeOtraSedeSeGuarda() {
        assertNull(procesador.procesar(Mensaje.actualizacionStock("LIM", "B01", 3)));
        assertEquals(0, catalogo.actualizaciones);

        assertNull(procesador.procesar(Mensaje.actualizacionStock("SJO", "C01", 7)));
        assertEquals(1, catalogo.actualizaciones);
        assertEquals("SJO", catalogo.ultimaSede);
        assertEquals("C01", catalogo.ultimoProducto);
        assertEquals(7, catalogo.ultimaCantidad);
    }

    @Test
    void mensajeParaOtraSedeSeIgnora() {
        assertNull(procesador.procesar(Mensaje.solicitarReserva("ALA", "SJO", "P7", "B01", 2)));
        assertNull(procesador.procesar(Mensaje.liberarReserva("ALA", "SJO", "P7", "B01")));
        assertEquals(5, inventario.stockDe("B01"));
        assertEquals(0, inventario.liberaciones);
    }

    @Test
    void mensajesMalosSeIgnoranSinExcepcion() {
        assertNull(procesador.procesar(null));
        assertNull(procesador.procesar(Mensaje.desdeJson("{\"tipo\": \"BORRAR_TODO\", \"sedeOrigen\": \"ALA\"}")));
        assertNull(procesador.procesar(Mensaje.desdeJson("{\"tipo\": \"SOLICITAR_RESERVA\", \"sedeOrigen\": \"ALA\", "
                + "\"sedeDestino\": \"LIM\", \"idPedido\": \"P7\"}"))); // sin producto ni cantidad
        assertNull(procesador.procesar(Mensaje.desdeJson("{\"tipo\": \"ACTUALIZACION_STOCK\", \"sedeOrigen\": \"SJO\"}")));
        assertEquals(0, catalogo.actualizaciones);
    }

    @Test
    void errorDelInventarioRespondeRechazo() {
        inventario.fallar = true;
        Mensaje r = procesador.procesar(Mensaje.solicitarReserva("ALA", "LIM", "P7", "B01", 2));
        assertFalse(r.isExito());
        assertTrue(r.getMotivo().contains("archivo de inventario dañado"));
    }
}
