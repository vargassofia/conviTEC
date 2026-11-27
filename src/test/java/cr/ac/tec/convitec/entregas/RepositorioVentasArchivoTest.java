package cr.ac.tec.convitec.entregas;

import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RepositorioVentasArchivoTest {

    @TempDir
    Path carpeta;

    private Venta venta(String idPedido) {
        return new Venta("V-" + idPedido, idPedido, "San José", "Heredia",
                LocalDateTime.of(2026, 11, 14, 11, 0), "11:00",
                new DetalleVenta[]{new DetalleVenta("C01", "Café", 1, 1500)}, 20);
    }

    @Test
    void persisteEnCadaCambioYRecuperaAlReabrir() {
        RepositorioVentasArchivo primero = new RepositorioVentasArchivo(carpeta);
        primero.guardar(venta("P1"));

        RepositorioVentasArchivo reabierto = new RepositorioVentasArchivo(carpeta);
        assertEquals(1, reabierto.listar().length);
        assertEquals("V-P1", reabierto.buscarPorIdPedido("P1").getIdVenta());
        assertEquals(1500, reabierto.listar()[0].getMontoTotal());
    }

    @Test
    void conservaComportamientoDelRepositorioEnMemoria() {
        RepositorioVentasArchivo repo = new RepositorioVentasArchivo(carpeta);
        assertEquals(0, repo.listar().length);
        assertNull(repo.buscarPorIdPedido(null));
        assertNull(repo.buscarPorIdPedido("inexistente"));
        assertThrows(IllegalArgumentException.class, () -> repo.guardar(null));

        repo.guardar(venta("P1"));
        repo.guardar(venta("P2"));
        assertEquals("P1", repo.listar()[0].getIdPedido());
        assertEquals("P2", repo.listar()[1].getIdPedido());
        assertThrows(IllegalStateException.class, () -> repo.guardar(venta("P1")));
        assertEquals(2, repo.getCantidad());
    }
}
