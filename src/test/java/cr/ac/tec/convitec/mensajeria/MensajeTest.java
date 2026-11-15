package cr.ac.tec.convitec.mensajeria;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MensajeTest {

    /** Ida y vuelta a JSON: todos los campos deben quedar iguales. */
    private static Mensaje idaYVuelta(Mensaje original) {
        Mensaje copia = Mensaje.desdeJson(original.aJson());
        assertEquals(original.getId(), copia.getId());
        assertEquals(original.getTipo(), copia.getTipo());
        assertEquals(original.getSedeOrigen(), copia.getSedeOrigen());
        assertEquals(original.getSedeDestino(), copia.getSedeDestino());
        assertEquals(original.getCorrelationId(), copia.getCorrelationId());
        assertEquals(original.getIdPedido(), copia.getIdPedido());
        assertEquals(original.getCodigoProducto(), copia.getCodigoProducto());
        assertEquals(original.getCantidad(), copia.getCantidad());
        assertEquals(original.isExito(), copia.isExito());
        assertEquals(original.getMotivo(), copia.getMotivo());
        assertEquals(original.getFechaHora(), copia.getFechaHora());
        return copia;
    }

    @Test
    void solicitarReserva() {
        Mensaje m = idaYVuelta(Mensaje.solicitarReserva("ALA", "LIM", "P7", "B01", 2));
        assertEquals(TipoMensaje.SOLICITAR_RESERVA, m.getTipo());
        assertEquals(m.getId(), m.getCorrelationId());
        assertEquals(2, m.getCantidad());
    }

    @Test
    void respuestaReservaLlevaElMismoCorrelationId() {
        Mensaje solicitud = Mensaje.solicitarReserva("ALA", "LIM", "P7", "B01", 2);
        Mensaje r = idaYVuelta(Mensaje.respuestaReserva(solicitud, "LIM", false,
                "stock insuficiente de B01 (hay 1, se piden 2)"));
        assertEquals(TipoMensaje.RESPUESTA_RESERVA, r.getTipo());
        assertEquals(solicitud.getCorrelationId(), r.getCorrelationId());
        assertEquals("LIM", r.getSedeOrigen());
        assertEquals("ALA", r.getSedeDestino());
        assertNotEquals(solicitud.getId(), r.getId());
        assertEquals("stock insuficiente de B01 (hay 1, se piden 2)", r.getMotivo());
        assertEquals("Limón ñandú", idaYVuelta(Mensaje.respuestaReserva(solicitud, "LIM", false, "Limón ñandú"))
                .getMotivo()); // las tildes sobreviven el JSON
    }

    @Test
    void confirmarYLiberar() {
        Mensaje c = idaYVuelta(Mensaje.confirmarReserva("ALA", "LIM", "P7", "B01"));
        assertEquals(TipoMensaje.CONFIRMAR_RESERVA, c.getTipo());
        assertNull(c.getCantidad());
        Mensaje l = idaYVuelta(Mensaje.liberarReserva("ALA", "LIM", "P7", "B01"));
        assertEquals(TipoMensaje.LIBERAR_RESERVA, l.getTipo());
    }

    @Test
    void actualizacionStockSinDestino() {
        Mensaje a = idaYVuelta(Mensaje.actualizacionStock("LIM", "B01", 0));
        assertEquals(TipoMensaje.ACTUALIZACION_STOCK, a.getTipo());
        assertNull(a.getSedeDestino());
        assertEquals(0, a.getCantidad());
    }

    @Test
    void idsUnicos() {
        String anterior = null;
        for (int i = 0; i < 1000; i++) {
            String id = Mensaje.liberarReserva("ALA", "LIM", "P" + i, "B01").getId();
            assertNotEquals(anterior, id);
            anterior = id;
        }
    }

    @Test
    void jsonMalFormado() {
        assertThrows(MensajeriaException.class, () -> Mensaje.desdeJson("{ tipo: "));
        assertThrows(MensajeriaException.class, () -> Mensaje.desdeJson("[1, 2]"));
        assertThrows(MensajeriaException.class, () -> Mensaje.desdeJson(""));
        assertThrows(MensajeriaException.class, () -> Mensaje.desdeJson(null));
        assertThrows(MensajeriaException.class, () -> Mensaje.desdeJson("{\"cantidad\": \"muchas\"}"));
    }

    @Test
    void tipoDesconocidoQuedaNulo() {
        assertNull(Mensaje.desdeJson("{\"tipo\": \"BORRAR_TODO\"}").getTipo());
    }
}
