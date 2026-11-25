package cr.ac.tec.convitec.catalogo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AVLTreeTest {

    @Test
    void arbolAVLGenericoOrdenaInsertaBuscaYElimina() {
        AVLTree<String, Integer> arbol = new AVLTree<>();

        assertTrue(arbol.insertar("P02", 20));
        assertTrue(arbol.insertar("P01", 10));
        assertTrue(arbol.insertar("P03", 30));
        assertFalse(arbol.insertar("P02", 99));
        assertTrue(arbol.esValido());

        assertArrayEquals(new Object[]{"P01", "P02", "P03"}, arbol.clavesEnOrden());
        assertEquals(20, arbol.buscar("P02"));
        assertTrue(arbol.actualizar("P02", 25));
        assertEquals(25, arbol.buscar("P02"));
        assertTrue(arbol.eliminar("P02"));
        assertTrue(arbol.esValido());
        assertArrayEquals(new Object[]{"P01", "P03"}, arbol.clavesEnOrden());
    }

    @Test
    void claveNulaEsRechazada() {
        AVLTree<String, Integer> arbol = new AVLTree<>();

        assertThrows(IllegalArgumentException.class, () -> arbol.insertar(null, 1));
    }
}
