package cr.ac.tec.convitec.grafo;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class MinHeapTest {

    @Test
    void extraeEnterosEnOrdenAscendenteYCrece() {
        MinHeap<Integer> heap = new MinHeap<>(Integer::compare);
        int[] valores = {8, 3, 10, 1, 6, 2, 9, 4, 7, 5, 0, 3};

        for (int valor : valores) {
            heap.insertar(valor);
        }

        assertEquals(valores.length, heap.getTamano());
        for (int esperado = 0; esperado <= 10; esperado++) {
            assertEquals(esperado, heap.extraerMinimo());
            if (esperado == 3) {
                assertEquals(3, heap.extraerMinimo());
            }
        }
        assertTrue(heap.estaVacio());
        assertEquals(0, heap.getTamano());
    }

    @Test
    void usaElComparadorParaOrdenarObjetos() {
        MinHeap<Tarea> heap = new MinHeap<>(Comparator.comparingInt(Tarea::prioridad));
        Tarea urgente = new Tarea("urgente", 1);
        Tarea normal = new Tarea("normal", 5);
        Tarea media = new Tarea("media", 3);

        heap.insertar(normal);
        heap.insertar(urgente);
        heap.insertar(media);

        assertSame(urgente, heap.verMinimo());
        assertSame(urgente, heap.extraerMinimo());
        assertSame(media, heap.extraerMinimo());
        assertSame(normal, heap.extraerMinimo());
    }

    @Test
    void rechazaOperacionesSinElementosYElementosNulos() {
        MinHeap<Integer> heap = new MinHeap<>(Integer::compare);

        assertThrows(NoSuchElementException.class, heap::verMinimo);
        assertThrows(NoSuchElementException.class, heap::extraerMinimo);
        assertThrows(NullPointerException.class, () -> heap.insertar(null));
        assertThrows(NullPointerException.class, () -> new MinHeap<Integer>(null));
    }

    private static final class Tarea {
        private final String nombre;
        private final int prioridad;

        private Tarea(String nombre, int prioridad) {
            this.nombre = nombre;
            this.prioridad = prioridad;
        }

        private int prioridad() {
            return prioridad;
        }
    }
}
