package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.mensajeria.RespuestaReserva;

/**
 * Mensajería entre sedes (implementación: cr.ac.tec.convitec.mensajeria.MensajeriaRabbit).
 *
 * Las sedes se identifican por su código: LIB, ALA, SJO o LIM.
 * Todos los métodos lanzan IllegalArgumentException si un argumento es nulo o inválido,
 * si el código de sede no existe o si una sede se intenta enviar mensajes a sí misma.
 */
public interface IMensajeria {

    /** Conecta al broker y empieza a recibir mensajes. Lanza MensajeriaException si el broker no responde. */
    void iniciar();

    /** Cierra la conexión. Nunca lanza excepciones. */
    void cerrar();

    /**
     * Pide a otra sede que reserve unidades y ESPERA su respuesta (hasta reserva.timeoutMs).
     * Nunca lanza excepción por problemas de red: si se vence el tiempo o el envío falla,
     * devuelve exito = false con el motivo (ej. "Limón no respondió a tiempo").
     */
    RespuestaReserva solicitarReserva(String sedeDestino, String idPedido, String codigoProducto, int cantidad);

    /** Pide a la otra sede descontar definitivamente lo reservado (no espera respuesta). */
    void confirmarReserva(String sedeDestino, String idPedido, String codigoProducto);

    /** Pide a la otra sede devolver lo reservado a su inventario (no espera respuesta). */
    void liberarReserva(String sedeDestino, String idPedido, String codigoProducto);

    /** Avisa a TODAS las sedes el stock nuevo de un producto de esta sede (cantidadNueva >= 0). */
    void publicarActualizacionStock(String codigoProducto, int cantidadNueva);
}
