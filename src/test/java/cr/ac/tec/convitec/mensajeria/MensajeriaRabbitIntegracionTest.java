package cr.ac.tec.convitec.mensajeria;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.mensajeria.ProcesadorMensajesTest.CatalogoFalso;
import cr.ac.tec.convitec.mensajeria.ProcesadorMensajesTest.InventarioFalso;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba con un broker RabbitMQ REAL en localhost:5672.
 * Si el broker no está corriendo, todas las pruebas de esta clase se SALTAN
 * (para correrlas: brew services start rabbitmq).
 *
 * Usa una topología con sufijo aleatorio para no chocar con las colas de las sedes reales,
 * y al final borra sus colas y exchanges.
 */
class MensajeriaRabbitIntegracionTest {

    private static final String SUFIJO = ".prueba-" + System.currentTimeMillis();
    private static final TopologiaRabbit TOPOLOGIA = new TopologiaRabbit(SUFIJO);

    private static InventarioFalso inventarioALA;
    private static InventarioFalso inventarioLIM;
    private static CatalogoFalso catalogoALA;
    private static CatalogoFalso catalogoLIM;
    private static MensajeriaRabbit ala;
    private static MensajeriaRabbit lim;

    private static ConfigSede config(String codigo, String nombre, int timeoutMs) {
        return ConfigSede.desdeTexto(String.join("\n",
                "sede.codigo=" + codigo,
                "sede.nombre=" + nombre,
                "http.puerto=8080",
                "rabbit.host=localhost",
                "rabbit.puerto=5672",
                "rabbit.usuario=guest",
                "rabbit.clave=guest",
                "reserva.timeoutMs=" + timeoutMs,
                "datos.carpeta=datos/" + codigo), "prueba-" + codigo);
    }

    private static boolean brokerDisponible() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 5672), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void iniciarSedes() {
        assumeTrue(brokerDisponible(), "RabbitMQ no está corriendo en localhost:5672: se saltan las pruebas");

        inventarioALA = new InventarioFalso(new String[]{"A01"}, new int[]{10});
        inventarioLIM = new InventarioFalso(new String[]{"B01", "B02"}, new int[]{5, 1});
        catalogoALA = new CatalogoFalso();
        catalogoLIM = new CatalogoFalso();

        ala = new MensajeriaRabbit(config("ALA", "Alajuela", 2000), inventarioALA, catalogoALA, TOPOLOGIA);
        lim = new MensajeriaRabbit(config("LIM", "Limón", 2000), inventarioLIM, catalogoLIM, TOPOLOGIA);
        ala.iniciar();
        lim.iniciar();
    }

    @AfterAll
    static void cerrarYLimpiar() {
        if (ala != null) {
            ala.cerrar();
        }
        if (lim != null) {
            lim.cerrar();
        }
        if (!brokerDisponible()) {
            return;
        }
        ConnectionFactory f = new ConnectionFactory();
        f.setHost("localhost");
        try (Connection c = f.newConnection(); Channel canal = c.createChannel()) {
            String[] sedes = {"ALA", "LIM"};
            for (String s : sedes) {
                canal.queueDelete(TOPOLOGIA.colaSede(s));
                canal.queueDelete(TOPOLOGIA.colaStock(s));
            }
            canal.exchangeDelete(TOPOLOGIA.exchangeDirecto());
            canal.exchangeDelete(TOPOLOGIA.exchangeStock());
        } catch (Exception e) {
            System.err.println("No se pudo limpiar la topología de prueba: " + e.getMessage());
        }
    }

    /** Condición que se revisa mientras se espera un mensaje asíncrono. */
    private interface Condicion {
        boolean cumple();
    }

    /** Espera (como máximo maxMs) a que la condición se cumpla. */
    private static boolean esperarHasta(Condicion condicion, long maxMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < limite) {
            if (condicion.cumple()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condicion.cumple();
    }

    @Test
    void reservaConStockYConfirmarActualizaElCatalogoDeLaOtraSede() throws InterruptedException {
        RespuestaReserva r = ala.solicitarReserva("LIM", "P1", "B01", 2);
        assertTrue(r.isExito(), "debió reservar: " + r);
        assertEquals("LIM", r.getSede());
        assertEquals(3, inventarioLIM.stockDe("B01"));

        ala.confirmarReserva("LIM", "P1", "B01");
        assertTrue(esperarHasta(() -> catalogoALA.getActualizaciones() > 0, 5000),
                "ALA no recibió la actualización de stock");
        synchronized (catalogoALA) {
            assertEquals("LIM", catalogoALA.ultimaSede);
            assertEquals("B01", catalogoALA.ultimoProducto);
            assertEquals(3, catalogoALA.ultimaCantidad);
        }
        assertEquals(0, catalogoLIM.getActualizaciones(), "LIM debió ignorar su propia actualización");
    }

    @Test
    void reservaSinStockDaRechazoConMotivo() {
        RespuestaReserva r = ala.solicitarReserva("LIM", "P2", "B02", 2);
        assertFalse(r.isExito());
        assertEquals("Limón: stock insuficiente de B02 (hay 1, se piden 2)", r.getMotivo());
    }

    @Test
    void liberarDevuelveLasUnidades() throws InterruptedException {
        assertTrue(ala.solicitarReserva("LIM", "P3", "B02", 1).isExito());
        assertEquals(0, inventarioLIM.stockDe("B02"));
        int antes = inventarioLIM.getLiberaciones();

        ala.liberarReserva("LIM", "P3", "B02");
        assertTrue(esperarHasta(() -> inventarioLIM.getLiberaciones() > antes, 5000), "LIM no recibió el LIBERAR");
        assertEquals(1, inventarioLIM.stockDe("B02"));
    }

    @Test
    void sedeQueNoEstaCorriendoNoResponde() {
        long inicio = System.currentTimeMillis();
        RespuestaReserva r = ala.solicitarReserva("SJO", "P4", "C01", 1);
        assertFalse(r.isExito());
        assertTrue(r.getMotivo().contains("no respondió"), r.getMotivo());
        assertEquals("San José no respondió a tiempo", r.getMotivo());
        assertTrue(System.currentTimeMillis() - inicio >= 1900, "debió esperar el timeout");
    }

    @Test
    void argumentosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> ala.solicitarReserva("XYZ", "P5", "B01", 1));
        assertThrows(IllegalArgumentException.class, () -> ala.solicitarReserva("ALA", "P5", "B01", 1));
        assertThrows(IllegalArgumentException.class, () -> ala.solicitarReserva("LIM", "P5", "B01", 0));
        assertThrows(IllegalArgumentException.class, () -> ala.solicitarReserva("LIM", null, "B01", 1));
        assertThrows(IllegalArgumentException.class, () -> ala.confirmarReserva(null, "P5", "B01"));
        assertThrows(IllegalArgumentException.class, () -> ala.publicarActualizacionStock("B01", -1));
    }
}
