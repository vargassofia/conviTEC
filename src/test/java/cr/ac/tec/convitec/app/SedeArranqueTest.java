package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.app.ConsolaSedeTest.MensajeriaFalsa;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.config.SedesConocidas;
import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.Venta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que una sede COMPLETA arranca con las dependencias de Sede.conDemostracion
 * (hoy las de demo) y una mensajería falsa, sin RabbitMQ.
 *
 * Persona 2: cuando reemplacen las clases de demo en Sede.conDemostracion, corran
 * "mvn clean test". Si esta prueba falla, el cambio rompió el arranque de la sede o alguno
 * de los contratos básicos (no lanzar excepciones en casos normales, idempotencia, listas vacías).
 */
class SedeArranqueTest {

    @Test
    void lasCuatroSedesArrancanYCierran() {
        String[] codigos = SedesConocidas.codigos();
        for (int i = 0; i < codigos.length; i++) {
            String codigo = codigos[i];
            ConfigSede config = ConfigSede.cargarDesdeRecurso("sedes/sede-" + codigo + ".properties");
            MensajeriaFalsa mensajeria = new MensajeriaFalsa();

            Sede sede = Sede.conDemostracion(config, mensajeria);
            sede.iniciar();
            assertTrue(sede.estaIniciada(), codigo + " no quedó iniciada");
            assertEquals(1, mensajeria.inicios);

            // Todas las piezas existen
            assertNotNull(sede.getInventario(), codigo + ": falta inventario");
            assertNotNull(sede.getCatalogo(), codigo + ": falta catálogo");
            assertNotNull(sede.getRepositorioVentas(), codigo + ": falta repositorio de ventas");
            assertNotNull(sede.getServicioRutas());
            assertNotNull(sede.getEstadisticas());
            assertSame(mensajeria, sede.getMensajeria());

            // Rutas: la sede está en el grafo
            ResultadoRuta aSiMisma = sede.getServicioRutas().caminoMasCorto(sede.getNombre(), sede.getNombre());
            assertEquals(0, aSiMisma.getTiempoTotal());

            sede.cerrar();
            sede.cerrar(); // dos veces no falla
            assertEquals(1, mensajeria.cierres);
            assertFalse(sede.estaIniciada());
        }
    }

    @Test
    void contratosBasicosDeLasDependencias() {
        ConfigSede config = ConfigSede.cargarDesdeRecurso("sedes/sede-ALA.properties");
        Sede sede = Sede.conDemostracion(config, new MensajeriaFalsa());
        sede.iniciar();
        try {
            // IInventarioLocal: un producto que no existe se rechaza con motivo, sin excepción
            RespuestaReserva r = sede.getInventario().reservar("PRUEBA-ARRANQUE", "NO-EXISTE-999", 1);
            assertNotNull(r);
            assertFalse(r.isExito());
            assertNotNull(r.getMotivo());

            // IInventarioLocal: confirmar y liberar sobre una reserva inexistente no fallan (idempotentes)
            assertDoesNotThrow(() -> sede.getInventario().liberar("PRUEBA-ARRANQUE", "NO-EXISTE-999"));
            assertDoesNotThrow(() -> sede.getInventario().liberar("PRUEBA-ARRANQUE", "NO-EXISTE-999"));
            assertDoesNotThrow(() -> sede.getInventario().confirmar("PRUEBA-ARRANQUE", "NO-EXISTE-999"));

            // ICatalogoGlobal: recibir una actualización de otra sede no falla
            assertDoesNotThrow(() -> sede.getCatalogo().actualizarStock("LIM", "B01", 3));

            // IRepositorioVentas: listar nunca devuelve null; buscar algo inexistente da null
            Venta[] ventas = sede.getRepositorioVentas().listar();
            assertNotNull(ventas);
            assertNull(sede.getRepositorioVentas().buscarPorIdPedido("PRUEBA-ARRANQUE-NO-EXISTE"));

            // Estadísticas funcionan sobre el repositorio de la sede
            assertNotNull(sede.getEstadisticas().historialVentas());
        } finally {
            sede.cerrar();
        }
    }

    @Test
    void sinConfiguracionNoArranca() {
        assertThrows(IllegalArgumentException.class, () -> Sede.conDemostracion(null));
        assertThrows(IllegalArgumentException.class, () -> Sede.conDemostracion(null, new MensajeriaFalsa()));
    }
}
