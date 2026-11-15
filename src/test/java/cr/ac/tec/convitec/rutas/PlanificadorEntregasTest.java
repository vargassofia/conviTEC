package cr.ac.tec.convitec.rutas;

import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.GrafoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanificadorEntregasTest {

    private Grafo grafo;

    @BeforeEach
    void preparar() {
        grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
    }

    private static SolicitudEntrega s(String id, String destino) {
        return new SolicitudEntrega(id, destino);
    }

    /** Revisa destino, tramo y acumulado de una parada. */
    private static void revisarParada(ParadaEntrega p, int numero, String destino, int tramo, int acumulado) {
        assertEquals(numero, p.getNumero());
        assertEquals(destino, p.getDestino());
        assertEquals(tramo, p.getTiempoTramo(), "tramo hacia " + destino);
        assertEquals(acumulado, p.getTiempoAcumulado(), "acumulado en " + destino);
    }

    // ------------------------------------------------------------------
    // Recorridos con el rutas.json real
    // ------------------------------------------------------------------

    @Test
    void sanJoseAgrupaPedidosDelMismoDestino() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "San José");
        RecorridoEntrega r = plan.planificarRecorridoCompleto(new SolicitudEntrega[]{
                s("P1", "Limón"), s("P2", "Heredia"), s("P3", "Cartago"), s("P4", "Heredia")
        }, SalidasDiarias.SALIDA_MANANA);

        ParadaEntrega[] p = r.getParadas();
        assertEquals(3, p.length);

        revisarParada(p[0], 1, "Heredia", 20, 20);
        assertArrayEquals(new String[]{"P2", "P4"}, p[0].getIdsPedidos());
        assertEquals("San José", p[0].getDesde());

        revisarParada(p[1], 2, "Cartago", 65, 85);
        assertArrayEquals(new String[]{"P3"}, p[1].getIdsPedidos());
        assertArrayEquals(new String[]{"Heredia", "San José", "Cartago"}, p[1].getCamino());

        revisarParada(p[2], 3, "Limón", 135, 220);
        assertArrayEquals(new String[]{"P1"}, p[2].getIdsPedidos());
        assertArrayEquals(new String[]{"Cartago", "Paraíso", "Turrialba", "Limón"}, p[2].getCamino());

        assertEquals(220, r.getTiempoTotal());
        assertEquals(4, r.getCantidadPedidosEntregados());
        assertEquals(0, r.getCantidadNoAlcanzables());
        assertEquals("San José", r.getSede());
        assertEquals("11:00", r.getHoraSalida());
    }

    @Test
    void liberiaSoloEntregaEnCanas() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "Liberia");
        RecorridoEntrega r = plan.planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("P1", "Cañas"), s("P2", "Cartago")}, "15:00");

        assertEquals(1, r.getCantidadParadas());
        revisarParada(r.getParadas()[0], 1, "Cañas", 65, 65);
        assertEquals(65, r.getTiempoTotal());
        assertEquals(1, r.getCantidadPedidosEntregados());

        assertArrayEquals(new String[]{"P2"}, r.getIdsNoAlcanzables());
        assertEquals("No hay ruta de Liberia a Cartago", r.getExplicacionesNoAlcanzables()[0]);
    }

    @Test
    void alajuelaPuntarenasOrotinaHeredia() {
        RecorridoEntrega r = new PlanificadorEntregas(grafo, "Alajuela").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("A", "Puntarenas"), s("B", "Orotina"), s("C", "Heredia")}, "11:00");
        ParadaEntrega[] p = r.getParadas();
        revisarParada(p[0], 1, "Heredia", 20, 20);
        revisarParada(p[1], 2, "Orotina", 75, 95);
        revisarParada(p[2], 3, "Puntarenas", 45, 140);
        assertEquals(140, r.getTiempoTotal());
    }

    @Test
    void limonSiquirresYLuegoTurrialbaPorGuapiles() {
        RecorridoEntrega r = new PlanificadorEntregas(grafo, "Limón").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("A", "Turrialba"), s("B", "Siquirres")}, "11:00");
        ParadaEntrega[] p = r.getParadas();
        revisarParada(p[0], 1, "Siquirres", 65, 65);
        revisarParada(p[1], 2, "Turrialba", 135, 200);
        assertArrayEquals(new String[]{"Siquirres", "Guápiles", "Turrialba"}, p[1].getCamino());
        assertEquals(200, r.getTiempoTotal());
    }

    @Test
    void alajuelaCuatroDestinos() {
        RecorridoEntrega r = new PlanificadorEntregas(grafo, "Alajuela").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("A", "Limón"), s("B", "Puntarenas"), s("C", "Guápiles"), s("D", "Paraíso")},
                "15:00");
        ParadaEntrega[] p = r.getParadas();
        assertEquals(4, p.length);
        revisarParada(p[0], 1, "Puntarenas", 80, 80);
        revisarParada(p[1], 2, "Paraíso", 175, 255);
        revisarParada(p[2], 3, "Limón", 120, 375);
        revisarParada(p[3], 4, "Guápiles", 120, 495);
        assertEquals(495, r.getTiempoTotal());
    }

    // ------------------------------------------------------------------
    // Casos especiales
    // ------------------------------------------------------------------

    @Test
    void pedidoALaPropiaSedeTieneTramoCero() {
        RecorridoEntrega r = new PlanificadorEntregas(grafo, "San José").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("P1", "Heredia"), s("P2", "san jose")}, "11:00");
        ParadaEntrega primera = r.getParadas()[0];
        revisarParada(primera, 1, "San José", 0, 0);
        assertArrayEquals(new String[]{"San José"}, primera.getCamino());
        assertArrayEquals(new String[]{"P2"}, primera.getIdsPedidos());
        assertEquals(20, r.getTiempoTotal());
    }

    @Test
    void pendientesVaciosONulos() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "Alajuela");
        RecorridoEntrega vacio = plan.planificarRecorridoCompleto(new SolicitudEntrega[0], "11:00");
        assertEquals(0, vacio.getCantidadParadas());
        assertEquals(0, vacio.getTiempoTotal());

        RecorridoEntrega nulo = plan.planificarRecorridoCompleto(null, "11:00");
        assertEquals(0, nulo.getCantidadParadas());
        assertEquals(0, nulo.getTiempoTotal());
        assertFalse(plan.hayEntregasPendientes());
    }

    @Test
    void destinoInexistenteVaANoAlcanzables() {
        RecorridoEntrega r = new PlanificadorEntregas(grafo, "Alajuela").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("P1", "Narnia"), s("P2", "Heredia")}, "11:00");
        assertEquals(1, r.getCantidadParadas());
        assertArrayEquals(new String[]{"P1"}, r.getIdsNoAlcanzables());
        assertTrue(r.getExplicacionesNoAlcanzables()[0].contains("Narnia"));
    }

    @Test
    void empateGanaElPrimeroDelArreglo() {
        // Desde Heredia, San José (R07) y Alajuela (R06) están ambos a 20 minutos.
        // Heredia no es una sede real, pero sirve para probar el empate.
        RecorridoEntrega r1 = new PlanificadorEntregas(grafo, "Heredia").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("P1", "San José"), s("P2", "Alajuela")}, "11:00");
        assertEquals("San José", r1.getParadas()[0].getDestino());

        RecorridoEntrega r2 = new PlanificadorEntregas(grafo, "Heredia").planificarRecorridoCompleto(
                new SolicitudEntrega[]{s("P1", "Alajuela"), s("P2", "San José")}, "11:00");
        assertEquals("Alajuela", r2.getParadas()[0].getDestino());
    }

    @Test
    void idsDuplicadosONulosLanzanError() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "San José");
        assertThrows(GrafoException.class, () -> plan.iniciarRecorrido(
                new SolicitudEntrega[]{s("P1", "Heredia"), s("P1", "Cartago")}, "11:00"));
        assertThrows(GrafoException.class, () -> plan.iniciarRecorrido(
                new SolicitudEntrega[]{s("P1", "Heredia"), s(" p1 ", "Cartago")}, "11:00"));
        assertThrows(GrafoException.class, () -> plan.iniciarRecorrido(
                new SolicitudEntrega[]{s(null, "Heredia")}, "11:00"));
        assertThrows(GrafoException.class, () -> plan.iniciarRecorrido(
                new SolicitudEntrega[]{s("P1", null)}, "11:00"));
        assertThrows(GrafoException.class, () -> plan.iniciarRecorrido(
                new SolicitudEntrega[]{null}, "11:00"));
    }

    @Test
    void sedeInexistenteOGrafoNuloLanzanError() {
        assertThrows(GrafoException.class, () -> new PlanificadorEntregas(grafo, "Narnia"));
        assertThrows(GrafoException.class, () -> new PlanificadorEntregas(null, "San José"));
        assertThrows(GrafoException.class, () -> new PlanificadorEntregas(new Grafo(), "San José"));
    }

    @Test
    void horaDeSalidaInvalida() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "San José");
        assertThrows(IllegalArgumentException.class,
                () -> plan.iniciarRecorrido(new SolicitudEntrega[0], "09:00"));
    }

    @Test
    void siguienteEntregaSinRecorridoOSinPendientes() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "San José");
        assertThrows(IllegalStateException.class, plan::calcularSiguienteEntrega);
        assertThrows(IllegalStateException.class, plan::obtenerRecorrido);

        plan.iniciarRecorrido(new SolicitudEntrega[]{s("P1", "Heredia")}, "11:00");
        plan.calcularSiguienteEntrega();
        assertFalse(plan.hayEntregasPendientes());
        assertThrows(IllegalStateException.class, plan::calcularSiguienteEntrega);
    }

    // ------------------------------------------------------------------
    // Paso a paso
    // ------------------------------------------------------------------

    @Test
    void pasoAPasoIgualAlCompleto() {
        SolicitudEntrega[] pendientes = {
                s("A", "Limón"), s("B", "Puntarenas"), s("C", "Guápiles"), s("D", "Paraíso"), s("E", "Cañas")
        };
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "Alajuela");
        RecorridoEntrega completo = plan.planificarRecorridoCompleto(pendientes, "11:00");

        plan.iniciarRecorrido(pendientes, "11:00");
        assertEquals("Alajuela", plan.getUbicacionActual());
        assertEquals(0, plan.getTiempoAcumulado());

        int i = 0;
        while (plan.hayEntregasPendientes()) {
            ParadaEntrega paso = plan.calcularSiguienteEntrega();
            ParadaEntrega esperada = completo.getParadas()[i];
            assertEquals(esperada.toString(), paso.toString());
            assertEquals(paso.getDestino(), plan.getUbicacionActual());
            assertEquals(paso.getTiempoAcumulado(), plan.getTiempoAcumulado());
            i++;
        }
        RecorridoEntrega pasoAPaso = plan.obtenerRecorrido();
        assertEquals(completo.toString(), pasoAPaso.toString());
        assertEquals(495, pasoAPaso.getTiempoTotal());
        assertArrayEquals(new String[]{"E"}, pasoAPaso.getIdsNoAlcanzables());
    }

    @Test
    void recorridoParcialMuestraLoHechoHastaElMomento() {
        PlanificadorEntregas plan = new PlanificadorEntregas(grafo, "San José");
        plan.iniciarRecorrido(new SolicitudEntrega[]{s("P1", "Limón"), s("P2", "Heredia")}, "15:00");
        plan.calcularSiguienteEntrega();

        RecorridoEntrega parcial = plan.obtenerRecorrido();
        assertEquals(1, parcial.getCantidadParadas());
        assertEquals(20, parcial.getTiempoTotal());
        assertTrue(plan.hayEntregasPendientes());
        assertFalse(parcial.toString().isBlank());
    }
}
