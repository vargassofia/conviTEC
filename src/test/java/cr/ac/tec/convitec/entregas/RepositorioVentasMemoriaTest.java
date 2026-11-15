package cr.ac.tec.convitec.entregas;

import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RepositorioVentasMemoriaTest {

    private static Venta venta(String idPedido) {
        return new Venta("V-" + idPedido, idPedido, "San José", "Heredia",
                LocalDateTime.of(2026, 11, 14, 11, 0), "11:00",
                new DetalleVenta[]{new DetalleVenta("C01", "Café", 1, 1500)}, 20);
    }

    @Test
    void creceMasAllaDeLaCapacidadInicial() {
        RepositorioVentasMemoria repo = new RepositorioVentasMemoria(2);
        for (int i = 1; i <= 5; i++) {
            repo.guardar(venta("P" + i));
        }
        Venta[] todas = repo.listar();
        assertEquals(5, todas.length);
        for (int i = 0; i < 5; i++) {
            assertEquals("P" + (i + 1), todas[i].getIdPedido()); // conserva el orden
        }
    }

    @Test
    void buscarExistenteEInexistente() {
        RepositorioVentasMemoria repo = new RepositorioVentasMemoria();
        repo.guardar(venta("P1"));
        assertEquals("V-P1", repo.buscarPorIdPedido("P1").getIdVenta());
        assertNull(repo.buscarPorIdPedido("P99"));
        assertNull(repo.buscarPorIdPedido(null));
    }

    @Test
    void listarVacio() {
        Venta[] vacio = new RepositorioVentasMemoria().listar();
        assertNotNull(vacio);
        assertEquals(0, vacio.length);
    }

    @Test
    void noGuardaNulosNiDuplicados() {
        RepositorioVentasMemoria repo = new RepositorioVentasMemoria();
        repo.guardar(venta("P1"));
        assertThrows(IllegalArgumentException.class, () -> repo.guardar(null));
        assertThrows(IllegalStateException.class, () -> repo.guardar(venta("P1")));
        assertEquals(1, repo.getCantidad());
    }
}
