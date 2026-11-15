package cr.ac.tec.convitec.grafo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DijkstraTest {

    private Grafo grafo;

    @BeforeEach
    void preparar() {
        grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
    }

    // ------------------------------------------------------------------
    // Tiempos y caminos con el rutas.json real
    // ------------------------------------------------------------------

    @Test
    void alajuelaLimonPorCartagoNoPorGuapiles() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "Alajuela", "Limón");
        assertTrue(r.esAlcanzable());
        assertEquals(215, r.getTiempoTotal()); // por Guápiles serían 225
        assertArrayEquals(new String[]{"Alajuela", "San José", "Cartago", "Paraíso", "Turrialba", "Limón"},
                r.getCamino());
    }

    @Test
    void sanJoseLimon() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "San José", "Limón");
        assertEquals(180, r.getTiempoTotal());
        assertArrayEquals(new String[]{"San José", "Cartago", "Paraíso", "Turrialba", "Limón"}, r.getCamino());
    }

    @Test
    void puntarenasSanJosePorAlajuelaNoPorOrotina() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "Puntarenas", "San José");
        assertEquals(115, r.getTiempoTotal());
        assertArrayEquals(new String[]{"Puntarenas", "Alajuela", "San José"}, r.getCamino());
    }

    @Test
    void herediaCartago() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "Heredia", "Cartago");
        assertEquals(65, r.getTiempoTotal());
        assertArrayEquals(new String[]{"Heredia", "San José", "Cartago"}, r.getCamino());
    }

    @Test
    void guapilesParaisoPorTurrialba() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "Guápiles", "Paraíso");
        assertEquals(125, r.getTiempoTotal());
        assertArrayEquals(new String[]{"Guápiles", "Turrialba", "Paraíso"}, r.getCamino());
    }

    @Test
    void siquirresTurrialbaPorGuapiles() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "Siquirres", "Turrialba");
        assertEquals(135, r.getTiempoTotal());
        assertArrayEquals(new String[]{"Siquirres", "Guápiles", "Turrialba"}, r.getCamino());
    }

    @Test
    void liberiaCanas() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "Liberia", "Cañas");
        assertEquals(65, r.getTiempoTotal());
        assertArrayEquals(new String[]{"Liberia", "Cañas"}, r.getCamino());
    }

    // ------------------------------------------------------------------
    // Casos especiales
    // ------------------------------------------------------------------

    @Test
    void destinosInalcanzablesNoLanzanExcepcion() {
        ResultadoRuta r1 = Dijkstra.caminoMasCorto(grafo, "Liberia", "Alajuela");
        assertFalse(r1.esAlcanzable());
        assertEquals(ResultadoRuta.SIN_RUTA, r1.getTiempoTotal());
        assertEquals(0, r1.getCamino().length);

        ResultadoRuta r2 = Dijkstra.caminoMasCorto(grafo, "Cañas", "Limón");
        assertFalse(r2.esAlcanzable());
        assertEquals(0, r2.getCamino().length);

        assertFalse(Dijkstra.existeRuta(grafo, "Liberia", "Alajuela"));
        assertFalse(Dijkstra.existeRuta(grafo, "Cañas", "Limón"));
        assertTrue(Dijkstra.existeRuta(grafo, "Alajuela", "Limón"));
    }

    @Test
    void mismoOrigenYDestino() {
        ResultadoRuta r = Dijkstra.caminoMasCorto(grafo, "San José", "San José");
        assertTrue(r.esAlcanzable());
        assertEquals(0, r.getTiempoTotal());
        assertArrayEquals(new String[]{"San José"}, r.getCamino());
    }

    @Test
    void ubicacionInexistenteLanzaError() {
        assertThrows(GrafoException.class, () -> Dijkstra.caminoMasCorto(grafo, "Narnia", "Limón"));
        assertThrows(GrafoException.class, () -> Dijkstra.caminoMasCorto(grafo, "Limón", "Narnia"));
        assertThrows(GrafoException.class, () -> Dijkstra.existeRuta(grafo, "Narnia", "Limón"));
        assertThrows(GrafoException.class, () -> Dijkstra.calcular(grafo, "Narnia"));
        assertThrows(GrafoException.class, () -> Dijkstra.calcular(grafo, 99));
    }

    @Test
    void busquedaToleranteDevuelveNombresOficiales() {
        ResultadoRuta normal = Dijkstra.caminoMasCorto(grafo, "San José", "Limón");
        ResultadoRuta tolerante = Dijkstra.caminoMasCorto(grafo, "san jose", "limon");
        assertEquals(normal.getTiempoTotal(), tolerante.getTiempoTotal());
        assertArrayEquals(normal.getCamino(), tolerante.getCamino());
        assertEquals("San José", tolerante.getOrigen());
        assertEquals("Limón", tolerante.getDestino());
    }

    @Test
    void grafoVacioLanzaError() {
        Grafo vacio = new Grafo();
        assertThrows(GrafoException.class, () -> Dijkstra.calcular(vacio, 0));
        assertThrows(GrafoException.class, () -> Dijkstra.caminoMasCorto(vacio, "A", "B"));
        assertThrows(GrafoException.class, () -> Dijkstra.calcular(null, 0));
    }

    // ------------------------------------------------------------------
    // ResultadoDijkstra (origen -> todos), lo que usará el planificador
    // ------------------------------------------------------------------

    @Test
    void desdeLiberiaNoHayDesbordamiento() {
        ResultadoDijkstra r = Dijkstra.calcular(grafo, "Liberia");
        int canas = grafo.indiceDe("Cañas");
        for (int i = 0; i < r.getCantidadVertices(); i++) {
            assertTrue(r.getDistancia(i) >= 0, "distancia negativa: desbordamiento");
            if (i != r.getOrigen() && i != canas) {
                assertFalse(r.esAlcanzable(i));
                assertEquals(Grafo.SIN_CONEXION, r.getDistancia(i));
                assertEquals(0, r.getCamino(i).length);
            }
        }
    }

    @Test
    void calcularDesdeSanJoseCoincideConCaminoMasCorto() {
        ResultadoDijkstra todos = Dijkstra.calcular(grafo, "San José");
        assertEquals("San José", todos.getNombreOrigen());
        for (int i = 0; i < grafo.getCantidadVertices(); i++) {
            ResultadoRuta uno = Dijkstra.caminoMasCorto(grafo, "San José", grafo.getNombre(i));
            if (uno.esAlcanzable()) {
                assertEquals(uno.getTiempoTotal(), todos.getDistancia(i));
                assertArrayEquals(uno.getCamino(), todos.getCamino(i));
            } else {
                assertFalse(todos.esAlcanzable(i));
            }
        }
        assertEquals(180, todos.getDistancia(grafo.indiceDe("Limón")));
    }
}
