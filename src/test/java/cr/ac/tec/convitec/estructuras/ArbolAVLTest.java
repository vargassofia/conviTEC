package cr.ac.tec.convitec.estructuras;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArbolAVLTest {

    @Test
    void arbolVacioYUnElemento() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();

        assertTrue(arbol.estaVacio());
        assertEquals(0, arbol.tamano());
        assertEquals(0, arbol.altura());
        assertNull(arbol.buscar("A"));
        assertFalse(arbol.contiene("A"));
        assertFalse(arbol.eliminar("A"));
        assertArrayEquals(new Object[0], arbol.valoresEnOrden());
        assertArrayEquals(new Object[0], arbol.clavesEnOrden());
        assertDoesNotThrow(() -> arbol.recorrerEnOrden((clave, valor) -> fail("No debe visitar nodos")));

        assertTrue(arbol.insertar("A", 10));
        assertFalse(arbol.estaVacio());
        assertEquals(1, arbol.tamano());
        assertEquals(1, arbol.altura());
        assertTrue(arbol.contiene("A"));
        assertEquals(10, arbol.buscar("A"));
        assertTrue(arbol.esValido());
    }

    @Test
    void insercionesAscendentesDescendentesYLasCuatroRotaciones() {
        ArbolAVL<String, Integer> ascendente = new ArbolAVL<>();
        ArbolAVL<String, Integer> descendente = new ArbolAVL<>();
        for (int i = 1; i <= 100; i++) {
            String clave = clave(i);
            assertTrue(ascendente.insertar(clave, i));
            assertTrue(ascendente.esValido());
            assertTrue(descendente.insertar(clave(101 - i), 101 - i));
            assertTrue(descendente.esValido());
        }
        assertEquals(ascendente.clavesEnOrden().length, descendente.clavesEnOrden().length);

        verificarRotacion(new String[]{"30", "20", "10"});
        verificarRotacion(new String[]{"10", "20", "30"});
        verificarRotacion(new String[]{"30", "10", "20"});
        verificarRotacion(new String[]{"10", "30", "20"});
    }

    @Test
    void rechazaDuplicadosSinReemplazarYActualiza() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        assertTrue(arbol.insertar("P01", 5));
        assertFalse(arbol.insertar("P01", 99));
        assertEquals(1, arbol.tamano());
        assertEquals(5, arbol.buscar("P01"));

        assertFalse(arbol.actualizar("P02", 8));
        assertTrue(arbol.actualizar("P01", 8));
        assertEquals(8, arbol.buscar("P01"));
    }

    @Test
    void buscarYActualizarClaveInexistente() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        arbol.insertar("P01", 1);

        assertNull(arbol.buscar("P99"));
        assertFalse(arbol.contiene("P99"));
        assertFalse(arbol.actualizar("P99", 99));
    }

    @Test
    void eliminaHojaNodoConUnHijoYNodoConDosHijos() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        for (String clave : new String[]{"20", "10", "30", "05", "25", "40"}) {
            arbol.insertar(clave, Integer.parseInt(clave));
        }

        assertTrue(arbol.eliminar("05"));
        assertTrue(arbol.esValido());
        assertTrue(arbol.eliminar("10"));
        assertTrue(arbol.esValido());
        assertTrue(arbol.eliminar("30"));
        assertTrue(arbol.esValido());
        assertFalse(arbol.eliminar("99"));
        assertArrayEquals(new Object[]{"20", "25", "40"}, arbol.clavesEnOrden());
    }

    @Test
    void eliminaRaizHastaVacioYPermiteReutilizar() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        arbol.insertar("20", 20);
        arbol.insertar("10", 10);
        arbol.insertar("30", 30);

        assertTrue(arbol.eliminar("20"));
        assertTrue(arbol.esValido());
        assertTrue(arbol.eliminar("10"));
        assertTrue(arbol.eliminar("30"));
        assertTrue(arbol.estaVacio());
        assertEquals(0, arbol.altura());

        assertTrue(arbol.insertar("40", 40));
        assertEquals(40, arbol.buscar("40"));
        assertTrue(arbol.esValido());
    }

    @Test
    void vaciarDejaElArbolListoParaReutilizar() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        arbol.insertar("B", 2);
        arbol.insertar("A", 1);
        arbol.insertar("C", 3);

        arbol.vaciar();
        assertTrue(arbol.estaVacio());
        assertEquals(0, arbol.tamano());
        assertEquals(0, arbol.altura());
        assertTrue(arbol.esValido());
        assertTrue(arbol.insertar("D", 4));
    }

    @Test
    void rechazaClavesNulasEnTodasLasOperacionesDeClave() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();

        assertEquals("La clave no puede ser null",
                assertThrows(IllegalArgumentException.class, () -> arbol.insertar(null, 1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> arbol.actualizar(null, 1));
        assertThrows(IllegalArgumentException.class, () -> arbol.buscar(null));
        assertThrows(IllegalArgumentException.class, () -> arbol.contiene(null));
        assertThrows(IllegalArgumentException.class, () -> arbol.eliminar(null));
    }

    @Test
    void recorreYDevuelveArreglosEnOrdenAscendente() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        arbol.insertar("P03", 30);
        arbol.insertar("P01", 10);
        arbol.insertar("P02", 20);

        Object[] claves = arbol.clavesEnOrden();
        Object[] valores = arbol.valoresEnOrden();
        assertArrayEquals(new Object[]{"P01", "P02", "P03"}, claves);
        assertArrayEquals(new Object[]{10, 20, 30}, valores);

        StringBuilder recorrido = new StringBuilder();
        arbol.recorrerEnOrden((clave, valor) -> recorrido.append(clave).append(':').append(valor).append(';'));
        assertEquals("P01:10;P02:20;P03:30;", recorrido.toString());

        claves[0] = "alterada";
        assertEquals("P01", arbol.clavesEnOrden()[0]);
    }

    @Test
    void milClavesCongruencialesMantienenBalanceEnCadaOperacion() {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        String[] claves = new String[1000];
        int estado = 1;

        for (int i = 0; i < claves.length; i++) {
            estado = (85 * estado + 1) % 1009;
            claves[i] = clave(estado);
            assertTrue(arbol.insertar(claves[i], i));
            verificarInvariantes(arbol);
        }

        for (String clave : claves) {
            assertTrue(arbol.eliminar(clave));
            verificarInvariantes(arbol);
        }
        assertTrue(arbol.estaVacio());
    }

    private void verificarRotacion(String[] claves) {
        ArbolAVL<String, Integer> arbol = new ArbolAVL<>();
        for (String clave : claves) {
            assertTrue(arbol.insertar(clave, Integer.parseInt(clave)));
        }
        assertTrue(arbol.esValido());
        assertEquals(2, arbol.altura());
        assertArrayEquals(new Object[]{"10", "20", "30"}, arbol.clavesEnOrden());
    }

    private void verificarInvariantes(ArbolAVL<String, Integer> arbol) {
        assertTrue(arbol.esValido());
        double logaritmoBaseDos = Math.log(arbol.tamano() + 2.0) / Math.log(2.0);
        assertTrue(arbol.altura() <= 1.44 * logaritmoBaseDos,
                "Altura " + arbol.altura() + " fuera del límite para n=" + arbol.tamano());
    }

    private String clave(int numero) {
        String clave = Integer.toString(numero);
        while (clave.length() < 4) {
            clave = "0" + clave;
        }
        return clave;
    }
}
