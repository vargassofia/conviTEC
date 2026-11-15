package cr.ac.tec.convitec.estadisticas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrdenamientoTest {

    private static final Comparador<Integer> ASCENDENTE = (a, b) -> Integer.compare(a, b);

    @Test
    void enterosDesordenados() {
        Integer[] r = Ordenamiento.ordenar(new Integer[]{5, 3, 9, 1, 7, 2}, ASCENDENTE);
        assertArrayEquals(new Integer[]{1, 2, 3, 5, 7, 9}, r);
    }

    @Test
    void enterosRepetidos() {
        Integer[] r = Ordenamiento.ordenar(new Integer[]{4, 1, 4, 2, 1, 4}, ASCENDENTE);
        assertArrayEquals(new Integer[]{1, 1, 2, 4, 4, 4}, r);
    }

    @Test
    void yaOrdenadoYEnOrdenInverso() {
        assertArrayEquals(new Integer[]{1, 2, 3, 4, 5},
                Ordenamiento.ordenar(new Integer[]{1, 2, 3, 4, 5}, ASCENDENTE));
        assertArrayEquals(new Integer[]{1, 2, 3, 4, 5},
                Ordenamiento.ordenar(new Integer[]{5, 4, 3, 2, 1}, ASCENDENTE));
    }

    @Test
    void descendenteConOtroComparador() {
        Integer[] r = Ordenamiento.ordenar(new Integer[]{2, 8, 5}, (a, b) -> Integer.compare(b, a));
        assertArrayEquals(new Integer[]{8, 5, 2}, r);
    }

    @Test
    void vacioYUnElemento() {
        assertEquals(0, Ordenamiento.ordenar(new Integer[0], ASCENDENTE).length);
        assertArrayEquals(new Integer[]{7}, Ordenamiento.ordenar(new Integer[]{7}, ASCENDENTE));
    }

    @Test
    void nulosLanzanError() {
        assertThrows(IllegalArgumentException.class, () -> Ordenamiento.ordenar(null, ASCENDENTE));
        assertThrows(IllegalArgumentException.class, () -> Ordenamiento.ordenar(new Integer[]{1}, null));
    }

    @Test
    void esEstable() {
        // Se ordena solo por la clave (primer carácter); la letra muestra el orden original
        String[] datos = {"2a", "1a", "2b", "1b", "2c", "1c", "3a"};
        String[] r = Ordenamiento.ordenar(datos, (a, b) -> Character.compare(a.charAt(0), b.charAt(0)));
        assertArrayEquals(new String[]{"1a", "1b", "1c", "2a", "2b", "2c", "3a"}, r);
    }

    @Test
    void noModificaElOriginal() {
        Integer[] original = {3, 1, 2};
        Integer[] r = Ordenamiento.ordenar(original, ASCENDENTE);
        assertArrayEquals(new Integer[]{3, 1, 2}, original);
        assertNotSame(original, r);
    }
}
