package cr.ac.tec.convitec.grafo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrafoTest {

    private Grafo grafo;

    @BeforeEach
    void preparar() {
        grafo = new Grafo();
    }

    @Test
    void grafoNuevoEstaVacio() {
        assertEquals(0, grafo.getCantidadVertices());
        assertEquals(0, grafo.getCantidadAristas());
        assertEquals(-1, grafo.indiceDe("San José"));
    }

    @Test
    void aristaEsBidireccional() {
        grafo.agregarArista("Alajuela", "Heredia", 20, "R06");
        assertEquals(20, grafo.getPeso("Alajuela", "Heredia"));
        assertEquals(20, grafo.getPeso("Heredia", "Alajuela"));
        assertEquals("R06", grafo.getIdRuta(grafo.indiceDe("Heredia"), grafo.indiceDe("Alajuela")));
    }

    @Test
    void diagonalEsCeroYSinRutaEsSinConexion() {
        grafo.agregarArista("A", "B", 10);
        grafo.agregarVertice("C");
        int a = grafo.indiceDe("A");
        int c = grafo.indiceDe("C");
        assertEquals(0, grafo.getPeso(a, a));
        assertEquals(Grafo.SIN_CONEXION, grafo.getPeso(a, c));
        assertFalse(grafo.sonAdyacentes(a, c));
    }

    @Test
    void verticeRepetidoNoSeDuplica() {
        int primero = grafo.agregarVertice("San José");
        int segundo = grafo.agregarVertice("san jose");   // sin tilde y en minúscula
        assertEquals(primero, segundo);
        assertEquals(1, grafo.getCantidadVertices());
        assertEquals("San José", grafo.getNombre(primero)); // conserva el nombre original
    }

    @Test
    void rutaDuplicadaSeRechazaEnCualquierSentido() {
        grafo.agregarArista("Cartago", "Paraíso", 15, "R09");
        assertThrows(GrafoException.class, () -> grafo.agregarArista("Paraíso", "Cartago", 30, "R99"));
    }

    @Test
    void tiempoInvalidoSeRechaza() {
        assertThrows(GrafoException.class, () -> grafo.agregarArista("A", "B", 0));
        assertThrows(GrafoException.class, () -> grafo.agregarArista("A", "B", -5));
    }

    @Test
    void mismoOrigenYDestinoSeRechaza() {
        assertThrows(GrafoException.class, () -> grafo.agregarArista("Limón", "limon", 10));
    }

    @Test
    void nombreVacioSeRechaza() {
        assertThrows(GrafoException.class, () -> grafo.agregarVertice("   "));
        assertThrows(GrafoException.class, () -> grafo.agregarVertice(null));
    }

    @Test
    void ubicacionInexistenteLanzaError() {
        grafo.agregarArista("A", "B", 10);
        assertThrows(GrafoException.class, () -> grafo.getPeso("A", "Z"));
        assertThrows(GrafoException.class, () -> grafo.getPeso(0, 99));
    }

    @Test
    void creceCuandoSeLlenaLaCapacidad() {
        Grafo pequeno = new Grafo(2);
        pequeno.agregarArista("A", "B", 1);
        pequeno.agregarArista("B", "C", 2);
        pequeno.agregarArista("C", "D", 3);
        pequeno.agregarArista("D", "E", 4);
        assertEquals(5, pequeno.getCantidadVertices());
        assertEquals(1, pequeno.getPeso("A", "B"));
        assertEquals(4, pequeno.getPeso("E", "D"));
        assertEquals(Grafo.SIN_CONEXION, pequeno.getPeso("A", "E"));
    }
}
