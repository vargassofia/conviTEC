package cr.ac.tec.convitec.mensajeria;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RespuestasPendientesTest {

    private static Mensaje respuesta(String correlationIdDeSolicitud, String motivo) {
        // Se arma una solicitud con ese correlationId usando JSON, para controlar el id
        Mensaje solicitud = Mensaje.desdeJson("{\"tipo\":\"SOLICITAR_RESERVA\",\"sedeOrigen\":\"ALA\","
                + "\"sedeDestino\":\"LIM\",\"correlationId\":\"" + correlationIdDeSolicitud + "\"}");
        return Mensaje.respuestaReserva(solicitud, "LIM", true, motivo);
    }

    /** Completa desde otro hilo después de una pausa. */
    private static Thread completarLuego(RespuestasPendientes p, String id, Mensaje m, long pausaMs) {
        Thread hilo = new Thread(() -> {
            try {
                Thread.sleep(pausaMs);
            } catch (InterruptedException e) {
                return;
            }
            p.completar(id, m);
        });
        hilo.start();
        return hilo;
    }

    @Test
    void seCompletaDesdeOtroHiloAntesDelTimeout() throws InterruptedException {
        RespuestasPendientes p = new RespuestasPendientes();
        p.registrar("c1");
        Mensaje m = respuesta("c1", "ok");
        Thread hilo = completarLuego(p, "c1", m, 100);

        long inicio = System.currentTimeMillis();
        assertSame(m, p.esperar("c1", 5000));
        assertTrue(System.currentTimeMillis() - inicio < 4000, "no debió esperar el timeout completo");
        assertEquals(0, p.getCantidadPendientes()); // la entrada se eliminó
        hilo.join();
    }

    @Test
    void seVenceElTimeoutYDevuelveNull() {
        RespuestasPendientes p = new RespuestasPendientes();
        p.registrar("c1");
        long inicio = System.currentTimeMillis();
        assertNull(p.esperar("c1", 200));
        assertTrue(System.currentTimeMillis() - inicio >= 190);
        assertEquals(0, p.getCantidadPendientes());

        // La respuesta que llega tarde se ignora
        assertFalse(p.completar("c1", respuesta("c1", "tarde")));
    }

    @Test
    void correlationIdDesconocidoSeIgnora() {
        RespuestasPendientes p = new RespuestasPendientes();
        assertFalse(p.completar("no-existe", respuesta("no-existe", "x")));
        assertFalse(p.completar(null, null));
        assertNull(p.esperar("no-existe", 50));
    }

    @Test
    void variasSolicitudesALaVezNoSeMezclan() throws InterruptedException {
        RespuestasPendientes p = new RespuestasPendientes();
        int n = 20; // más que la capacidad inicial: también prueba que el arreglo crece
        Mensaje[] esperadas = new Mensaje[n];
        Mensaje[] recibidas = new Mensaje[n];
        Thread[] esperando = new Thread[n];
        Thread[] respondiendo = new Thread[n];

        for (int i = 0; i < n; i++) {
            p.registrar("c" + i);
            esperadas[i] = respuesta("c" + i, "respuesta " + i);
        }
        for (int i = 0; i < n; i++) {
            final int k = i;
            esperando[i] = new Thread(() -> recibidas[k] = p.esperar("c" + k, 5000));
            esperando[i].start();
        }
        // Se responden en orden inverso y con pausas distintas
        for (int i = n - 1; i >= 0; i--) {
            respondiendo[i] = completarLuego(p, "c" + i, esperadas[i], (n - i) * 5L);
        }
        for (int i = 0; i < n; i++) {
            esperando[i].join();
            respondiendo[i].join();
            assertSame(esperadas[i], recibidas[i], "la solicitud c" + i + " recibió otra respuesta");
        }
        assertEquals(0, p.getCantidadPendientes());
    }

    @Test
    void argumentosInvalidos() {
        RespuestasPendientes p = new RespuestasPendientes();
        assertThrows(IllegalArgumentException.class, () -> p.registrar(null));
        p.registrar("c1");
        assertThrows(IllegalArgumentException.class, () -> p.registrar("c1"));
        assertThrows(IllegalArgumentException.class, () -> p.esperar("c1", 0));
        p.cancelar("c1");
        assertEquals(0, p.getCantidadPendientes());
    }
}
