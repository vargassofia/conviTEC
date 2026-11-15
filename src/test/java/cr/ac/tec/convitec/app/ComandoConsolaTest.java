package cr.ac.tec.convitec.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComandoConsolaTest {

    @Test
    void comandoSinArgumentos() {
        ComandoConsola c = ComandoConsola.interpretar("STOCK");
        assertEquals("stock", c.getNombre());
        assertEquals(0, c.getCantidadArgumentos());
        assertFalse(c.esVacio());
    }

    @Test
    void comandoConArgumentos() {
        ComandoConsola c = ComandoConsola.interpretar("reservar LIM P7 B01 2");
        assertEquals("reservar", c.getNombre());
        assertArrayEquals(new String[]{"LIM", "P7", "B01", "2"}, c.getArgumentos());
        assertEquals(2, c.getEntero(3));
        assertThrows(IllegalArgumentException.class, () -> c.getEntero(2));   // "B01" no es número
        assertThrows(IllegalArgumentException.class, () -> c.getArgumento(4)); // no existe
    }

    @Test
    void espaciosExtra() {
        ComandoConsola c = ComandoConsola.interpretar("   agregar\t  C01     5   ");
        assertEquals("agregar", c.getNombre());
        assertArrayEquals(new String[]{"C01", "5"}, c.getArgumentos());
    }

    @Test
    void nombresConEspacio() {
        ComandoConsola comillas = ComandoConsola.interpretar("ruta \"San José\"");
        assertArrayEquals(new String[]{"San José"}, comillas.getArgumentos());

        ComandoConsola guion = ComandoConsola.interpretar("ruta San_José");
        assertEquals("San José", guion.getNombreUbicacion(0));

        ComandoConsola entregar = ComandoConsola.interpretar("entregar 11:00 \"P1:San José\" P2:Heredia");
        assertArrayEquals(new String[]{"11:00", "P1:San José", "P2:Heredia"}, entregar.getArgumentos());

        assertThrows(IllegalArgumentException.class, () -> ComandoConsola.interpretar("ruta \"San José"));
    }

    @Test
    void lineaVacia() {
        assertTrue(ComandoConsola.interpretar("").esVacio());
        assertTrue(ComandoConsola.interpretar("    ").esVacio());
        assertTrue(ComandoConsola.interpretar(null).esVacio());
        assertEquals(0, ComandoConsola.interpretar("").getCantidadArgumentos());
    }

    @Test
    void getArgumentosDevuelveCopia() {
        ComandoConsola c = ComandoConsola.interpretar("ruta Limón");
        c.getArgumentos()[0] = "otro";
        assertEquals("Limón", c.getArgumento(0));
    }
}
