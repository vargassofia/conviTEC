package cr.ac.tec.convitec.entregas;

import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;
import cr.ac.tec.convitec.servicios.IGestorPedidos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServicioEntregasTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 14, 11, 0);

    /** Gestor de pedidos falso, solo con arreglos (simula lo que hará la Persona 2). */
    static class GestorFalso implements IGestorPedidos {
        final String[] ids;
        final DetalleVenta[][] detalles;
        final boolean[] entregado;

        GestorFalso(String[] ids, DetalleVenta[][] detalles) {
            this.ids = ids;
            this.detalles = detalles;
            this.entregado = new boolean[ids.length];
        }

        int indice(String id) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i].equals(id)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public DetalleVenta[] obtenerDetalle(String idPedido) {
            int i = indice(idPedido);
            return i == -1 ? null : detalles[i];
        }

        @Override
        public void marcarEntregado(String idPedido, LocalDateTime fechaHora) {
            entregado[indice(idPedido)] = true;
        }

        boolean estaEntregado(String id) {
            return entregado[indice(id)];
        }
    }

    private Grafo grafo;
    private GestorFalso gestor;
    private RepositorioVentasMemoria repositorio;
    private ServicioEntregas entregas;
    private ServicioRutas rutas;

    private static final SolicitudEntrega[] PENDIENTES = {
            new SolicitudEntrega("P1", "Limón"), new SolicitudEntrega("P2", "Heredia"),
            new SolicitudEntrega("P3", "Cartago"), new SolicitudEntrega("P4", "Heredia")
    };

    private static GestorFalso gestorDeEjemplo() {
        return new GestorFalso(
                new String[]{"P1", "P2", "P3", "P4", "P5"},
                new DetalleVenta[][]{
                        {new DetalleVenta("C01", "Café", 2, 1500)},
                        {new DetalleVenta("P05", "Pan", 4, 1000)},
                        {new DetalleVenta("L10", "Leche", 1, 1200), new DetalleVenta("P05", "Pan", 1, 1000)},
                        {new DetalleVenta("A02", "Agua", 3, 800)},
                        {}  // P5 existe pero sin productos
                });
    }

    @BeforeEach
    void preparar() {
        grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        gestor = gestorDeEjemplo();
        repositorio = new RepositorioVentasMemoria();
        entregas = new ServicioEntregas("San José", gestor, repositorio);
        rutas = new ServicioRutas(grafo, "San José");
    }

    private static Venta buscar(Venta[] ventas, String idPedido) {
        for (int i = 0; i < ventas.length; i++) {
            if (ventas[i].getIdPedido().equals(idPedido)) {
                return ventas[i];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Escenario principal: sede San José, recorrido completo
    // ------------------------------------------------------------------

    @Test
    void registrarRecorridoCompletoCreaLasVentas() {
        RecorridoEntrega recorrido = rutas.planificarRecorridoCompleto(PENDIENTES, "11:00");
        Venta[] ventas = entregas.registrarRecorrido(recorrido, FECHA);

        assertEquals(4, ventas.length);
        assertEquals(20, buscar(ventas, "P2").getTiempoTraslado());
        assertEquals(20, buscar(ventas, "P4").getTiempoTraslado());
        assertEquals(85, buscar(ventas, "P3").getTiempoTraslado());
        assertEquals(220, buscar(ventas, "P1").getTiempoTraslado());

        Venta p3 = buscar(ventas, "P3");
        assertEquals("V-P3", p3.getIdVenta());
        assertEquals("San José", p3.getSede());
        assertEquals("Cartago", p3.getDestino());
        assertEquals("11:00", p3.getHoraSalida());
        assertEquals(FECHA, p3.getFechaHora());
        assertEquals(2200.0, p3.getMontoTotal());

        for (String id : new String[]{"P1", "P2", "P3", "P4"}) {
            assertTrue(gestor.estaEntregado(id), id + " debería estar entregado");
            assertNotNull(repositorio.buscarPorIdPedido(id));
        }
        assertEquals(4, repositorio.listar().length);
        assertFalse(gestor.estaEntregado("P5"));
    }

    @Test
    void paradaPorParadaDaLoMismoQueElRecorridoCompleto() {
        Venta[] completo = entregas.registrarRecorrido(rutas.planificarRecorridoCompleto(PENDIENTES, "11:00"), FECHA);

        GestorFalso gestor2 = gestorDeEjemplo();
        RepositorioVentasMemoria repo2 = new RepositorioVentasMemoria();
        ServicioEntregas entregas2 = new ServicioEntregas("San José", gestor2, repo2);
        rutas.iniciarRecorrido(PENDIENTES, "11:00");
        while (rutas.hayEntregasPendientes()) {
            ParadaEntrega parada = rutas.calcularSiguienteEntrega();
            entregas2.registrarParada(parada, "11:00", FECHA);
        }

        Venta[] pasoAPaso = repo2.listar();
        assertEquals(completo.length, pasoAPaso.length);
        for (int i = 0; i < completo.length; i++) {
            assertEquals(completo[i].toString(), pasoAPaso[i].toString());
        }
    }

    // ------------------------------------------------------------------
    // Casos especiales
    // ------------------------------------------------------------------

    @Test
    void pedidoInexistenteNoRegistraNadaDeEsaParada() {
        // P2 existe y P9 no; los dos van a Heredia, así que quedan en la misma parada
        RecorridoEntrega r = rutas.planificarRecorridoCompleto(new SolicitudEntrega[]{
                new SolicitudEntrega("P2", "Heredia"), new SolicitudEntrega("P9", "Heredia")}, "11:00");
        ParadaEntrega parada = r.getParadas()[0];
        assertEquals(2, parada.getCantidadPedidos());

        assertThrows(IllegalStateException.class, () -> entregas.registrarParada(parada, "11:00", FECHA));
        assertEquals(0, repositorio.listar().length);
        assertFalse(gestor.estaEntregado("P2"));
    }

    @Test
    void pedidoSinProductosNoSeRegistra() {
        RecorridoEntrega r = rutas.planificarRecorridoCompleto(new SolicitudEntrega[]{
                new SolicitudEntrega("P5", "Cartago")}, "11:00");
        assertThrows(IllegalStateException.class, () -> entregas.registrarRecorrido(r, FECHA));
        assertEquals(0, repositorio.listar().length);
    }

    @Test
    void pedidoYaVendidoNoSeDuplica() {
        RecorridoEntrega r = rutas.planificarRecorridoCompleto(PENDIENTES, "11:00");
        ParadaEntrega primera = r.getParadas()[0];
        entregas.registrarParada(primera, "11:00", FECHA);
        assertEquals(2, repositorio.listar().length);

        assertThrows(IllegalStateException.class, () -> entregas.registrarParada(primera, "11:00", FECHA));
        assertEquals(2, repositorio.listar().length);
    }

    @Test
    void recorridoVacioDaArregloVacio() {
        RecorridoEntrega vacio = rutas.planificarRecorridoCompleto(new SolicitudEntrega[0], "15:00");
        assertEquals(0, entregas.registrarRecorrido(vacio, FECHA).length);
        assertEquals(0, repositorio.listar().length);
    }

    @Test
    void argumentosNulosLanzanError() {
        RecorridoEntrega r = rutas.planificarRecorridoCompleto(PENDIENTES, "11:00");
        ParadaEntrega parada = r.getParadas()[0];

        assertThrows(IllegalArgumentException.class, () -> entregas.registrarParada(null, "11:00", FECHA));
        assertThrows(IllegalArgumentException.class, () -> entregas.registrarParada(parada, "11:00", null));
        assertThrows(IllegalArgumentException.class, () -> entregas.registrarParada(parada, "09:00", FECHA));
        assertThrows(IllegalArgumentException.class, () -> entregas.registrarRecorrido(null, FECHA));
        assertThrows(IllegalArgumentException.class, () -> entregas.registrarRecorrido(r, null));

        assertThrows(IllegalArgumentException.class, () -> new ServicioEntregas(null, gestor, repositorio));
        assertThrows(IllegalArgumentException.class, () -> new ServicioEntregas("San José", null, repositorio));
        assertThrows(IllegalArgumentException.class, () -> new ServicioEntregas("San José", gestor, null));
        assertEquals(0, repositorio.listar().length);
    }
}
