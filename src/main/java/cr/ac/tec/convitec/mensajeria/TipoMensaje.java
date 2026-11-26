package cr.ac.tec.convitec.mensajeria;

/**
 * Tipos de mensaje entre sedes.
 *
 * Flujo de un pedido que necesita productos de otra sede:
 *   SOLICITAR_RESERVA  (A -> B)  "apártame 2 unidades de B01 para el pedido P7"
 *   RESPUESTA_RESERVA  (B -> A)  "OK" o "rechazo: stock insuficiente..."
 *   CONFIRMAR_RESERVA  (A -> B)  "el pedido sigue: descuéntalas definitivamente"
 *   LIBERAR_RESERVA    (A -> B)  "el pedido se cayó: devuélvelas al inventario"
 *   ACTUALIZACION_STOCK (B -> todas)  "ahora tengo 5 unidades de B01"
 */
public enum TipoMensaje {
    SOLICITAR_RESERVA,
    RESPUESTA_RESERVA,
    CONFIRMAR_RESERVA,
    LIBERAR_RESERVA,
    ACTUALIZACION_STOCK
}
