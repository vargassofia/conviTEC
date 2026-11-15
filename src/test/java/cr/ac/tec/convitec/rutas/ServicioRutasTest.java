package cr.ac.tec.convitec.rutas;

import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.servicios.IServicioRutas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServicioRutasTest {

    private final IServicioRutas servicio =
            new ServicioRutas(CargadorGrafo.cargarDesdeRecurso("rutas.json"), "San José");

    @Test
    void consultasDeRutasDeleganEnDijkstra() {
        assertTrue(servicio.existeRuta("San José", "Limón"));
        assertFalse(servicio.existeRuta("San José", "Liberia"));
        assertEquals(180, servicio.caminoMasCorto("San José", "Limón").getTiempoTotal());
    }

    @Test
    void recorridoPasoAPasoYCompleto() {
        SolicitudEntrega[] pendientes = {
                new SolicitudEntrega("P1", "Limón"), new SolicitudEntrega("P2", "Heredia"),
                new SolicitudEntrega("P3", "Cartago"), new SolicitudEntrega("P4", "Heredia")
        };
        servicio.iniciarRecorrido(pendientes, "11:00");
        assertEquals("Heredia", servicio.calcularSiguienteEntrega().getDestino());
        assertTrue(servicio.hayEntregasPendientes());
        assertEquals(20, servicio.obtenerRecorrido().getTiempoTotal());

        assertEquals(220, servicio.planificarRecorridoCompleto(pendientes, "15:00").getTiempoTotal());
    }

    @Test
    void sedeInexistente() {
        assertThrows(GrafoException.class,
                () -> new ServicioRutas(CargadorGrafo.cargarDesdeRecurso("rutas.json"), "Narnia"));
    }
}
