package cr.ac.tec.convitec.grafo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CargadorGrafoTest {

    @Test
    void cargaElArchivoRealDeRutas() {
        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");

        assertEquals(13, grafo.getCantidadVertices());
        assertEquals(15, grafo.getCantidadAristas());
        assertEquals(35, grafo.getPeso("Alajuela", "San José"));
        assertEquals(75, grafo.getPeso("Limón", "Turrialba"));
        assertTrue(grafo.existeVertice("Cañas"));
        assertTrue(grafo.existeVertice("guapiles")); // sin tilde también funciona
    }

    @Test
    void liberiaSoloConectaConCanas() {
        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        int liberia = grafo.indiceDe("Liberia");
        int vecinos = 0;
        for (int j = 0; j < grafo.getCantidadVertices(); j++) {
            if (grafo.sonAdyacentes(liberia, j)) {
                vecinos++;
                assertEquals("Cañas", grafo.getNombre(j));
            }
        }
        assertEquals(1, vecinos);
    }

    @Test
    void archivoVacioOSinRutas() {
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto(""));
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto("{}"));
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto("{\"rutas\": []}"));
    }

    @Test
    void jsonMalFormado() {
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto("{ rutas: [ {"));
    }

    @Test
    void rutaSinTiempo() {
        String json = "{\"rutas\":[{\"id\":\"R01\",\"origen\":\"A\",\"destino\":\"B\"}]}";
        GrafoException e = assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto(json));
        assertTrue(e.getMessage().contains("tiempo"));
    }

    @Test
    void idRepetido() {
        String json = "{\"rutas\":["
                + "{\"id\":\"R01\",\"origen\":\"A\",\"destino\":\"B\",\"tiempo\":10},"
                + "{\"id\":\"R01\",\"origen\":\"B\",\"destino\":\"C\",\"tiempo\":20}]}";
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto(json));
    }

    @Test
    void parDuplicadoConOtroId() {
        String json = "{\"rutas\":["
                + "{\"id\":\"R01\",\"origen\":\"A\",\"destino\":\"B\",\"tiempo\":10},"
                + "{\"id\":\"R02\",\"origen\":\"B\",\"destino\":\"A\",\"tiempo\":10}]}";
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeTexto(json));
    }

    @Test
    void recursoInexistente() {
        assertThrows(GrafoException.class, () -> CargadorGrafo.cargarDesdeRecurso("no-existe.json"));
    }
}
