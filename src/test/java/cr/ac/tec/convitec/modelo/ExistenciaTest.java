package cr.ac.tec.convitec.modelo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExistenciaTest {

    @Test
    void constructoresValidosYGetters() {
        Existencia sinReservas = new Existencia("P-01", 10);
        assertEquals("P-01", sinReservas.getCodigoProducto());
        assertEquals(10, sinReservas.getCantidadDisponible());
        assertEquals(0, sinReservas.getCantidadReservada());
        assertEquals(10, sinReservas.getTotal());

        Existencia conReservas = new Existencia("P-02", 7, 3);
        assertEquals(7, conReservas.getCantidadDisponible());
        assertEquals(3, conReservas.getCantidadReservada());
        assertEquals(10, conReservas.getTotal());
    }

    @Test
    void constructoresRechazanCodigoInvalidoCantidadesNegativasYTotalDesbordado() {
        assertThrows(IllegalArgumentException.class, () -> new Existencia(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new Existencia("", 1));
        assertThrows(IllegalArgumentException.class, () -> new Existencia(" \t ", 1));
        assertThrows(IllegalArgumentException.class, () -> new Existencia("P-01", -1));
        assertThrows(IllegalArgumentException.class, () -> new Existencia("P-01", 1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Existencia("P-01", Integer.MAX_VALUE, 1));
    }

    @Test
    void reservarMueveUnidadesIncluyendoTodoElDisponible() {
        Existencia existencia = new Existencia("P-01", 10);

        assertTrue(existencia.reservar(4));
        assertEquals(6, existencia.getCantidadDisponible());
        assertEquals(4, existencia.getCantidadReservada());
        assertEquals(10, existencia.getTotal());

        assertTrue(existencia.reservar(6));
        assertEquals(0, existencia.getCantidadDisponible());
        assertEquals(10, existencia.getCantidadReservada());
        assertEquals(10, existencia.getTotal());
    }

    @Test
    void reservarSinStockSuficienteDevuelveFalseYSinCambios() {
        Existencia existencia = new Existencia("P-01", 3, 2);

        assertFalse(existencia.reservar(4));
        verificarEstado(existencia, 3, 2);
    }

    @Test
    void liberarDevuelveUnidadesAlDisponible() {
        Existencia existencia = new Existencia("P-01", 6, 4);

        existencia.liberar(3);

        verificarEstado(existencia, 9, 1);
    }

    @Test
    void confirmarDescuentaUnidadesReservadas() {
        Existencia existencia = new Existencia("P-01", 6, 4);

        existencia.confirmar(2);

        verificarEstado(existencia, 6, 2);
    }

    @Test
    void agregarAumentaDisponibleYPrevieneDesbordamientoSinCambios() {
        Existencia existencia = new Existencia("P-01", 10, 2);
        existencia.agregar(5);
        verificarEstado(existencia, 15, 2);

        Existencia cercaDelMaximo = new Existencia("P-02", Integer.MAX_VALUE - 1, 0);
        assertThrows(IllegalArgumentException.class, () -> cercaDelMaximo.agregar(2));
        verificarEstado(cercaDelMaximo, Integer.MAX_VALUE - 1, 0);

        Existencia totalCercaDelMaximo = new Existencia("P-03", Integer.MAX_VALUE - 2, 1);
        assertThrows(IllegalArgumentException.class, () -> totalCercaDelMaximo.agregar(2));
        verificarEstado(totalCercaDelMaximo, Integer.MAX_VALUE - 2, 1);
    }

    @Test
    void descontarRestaDisponible() {
        Existencia existencia = new Existencia("P-01", 10, 2);

        existencia.descontar(4);

        verificarEstado(existencia, 6, 2);
    }

    @Test
    void fallosPorCantidadInsuficienteNoCambianEstado() {
        Existencia existencia = new Existencia("P-01", 3, 2);

        assertThrows(IllegalArgumentException.class, () -> existencia.liberar(3));
        verificarEstado(existencia, 3, 2);
        assertThrows(IllegalArgumentException.class, () -> existencia.confirmar(3));
        verificarEstado(existencia, 3, 2);
        assertThrows(IllegalArgumentException.class, () -> existencia.descontar(4));
        verificarEstado(existencia, 3, 2);
    }

    @Test
    void todasLasOperacionesRechazanCantidadCeroONegativaSinCambios() {
        for (int cantidad : new int[]{0, -1}) {
            Existencia existencia = new Existencia("P-01", 5, 2);

            assertThrows(IllegalArgumentException.class, () -> existencia.reservar(cantidad));
            verificarEstado(existencia, 5, 2);
            assertThrows(IllegalArgumentException.class, () -> existencia.liberar(cantidad));
            verificarEstado(existencia, 5, 2);
            assertThrows(IllegalArgumentException.class, () -> existencia.confirmar(cantidad));
            verificarEstado(existencia, 5, 2);
            assertThrows(IllegalArgumentException.class, () -> existencia.agregar(cantidad));
            verificarEstado(existencia, 5, 2);
            assertThrows(IllegalArgumentException.class, () -> existencia.descontar(cantidad));
            verificarEstado(existencia, 5, 2);
        }
    }

    @Test
    void cicloCompletoDeReservaConfirmacionYLiberacion() {
        Existencia existencia = new Existencia("P-01", 10);

        assertTrue(existencia.reservar(4));
        verificarEstado(existencia, 6, 4);
        existencia.confirmar(4);
        verificarEstado(existencia, 6, 0);

        assertTrue(existencia.reservar(3));
        verificarEstado(existencia, 3, 3);
        existencia.liberar(3);
        verificarEstado(existencia, 6, 0);
    }

    @Test
    void copiaEsIndependienteDelOriginal() {
        Existencia original = new Existencia("P-01", 8, 2);
        Existencia copia = original.copia();

        assertNotSame(original, copia);
        assertEquals(original, copia);
        assertTrue(copia.reservar(3));

        verificarEstado(original, 8, 2);
        verificarEstado(copia, 5, 5);
    }

    @Test
    void igualdadHashYRepresentacionLegible() {
        Existencia primera = new Existencia("P-01", 8, 2);
        Existencia igual = new Existencia("P-01", 8, 2);
        Existencia distinta = new Existencia("P-01", 7, 3);

        assertEquals(primera, igual);
        assertEquals(primera.hashCode(), igual.hashCode());
        assertNotEquals(primera, distinta);
        assertNotEquals(null, primera);
        assertNotEquals("P-01", primera);
        assertTrue(primera.toString().contains("P-01"));
        assertTrue(primera.toString().contains("cantidadDisponible=8"));
        assertTrue(primera.toString().contains("cantidadReservada=2"));
    }

    private void verificarEstado(Existencia existencia, int disponible, int reservado) {
        assertEquals(disponible, existencia.getCantidadDisponible());
        assertEquals(reservado, existencia.getCantidadReservada());
        assertEquals(disponible + reservado, existencia.getTotal());
        assertTrue(existencia.getCantidadDisponible() >= 0);
        assertTrue(existencia.getCantidadReservada() >= 0);
    }
}
