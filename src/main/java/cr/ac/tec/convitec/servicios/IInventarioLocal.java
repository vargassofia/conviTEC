package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.mensajeria.RespuestaReserva;

/**
 * Inventario de ESTA sede, visto por la mensajería (lo implementa la Persona 2).
 *
 * Lo llama la mensajería cuando OTRA sede pide, confirma o libera una reserva.
 *
 * IMPORTANTE:
 * - Thread-safe: los mensajes llegan en otro hilo (el de RabbitMQ), al mismo tiempo que la
 *   interfaz puede estar vendiendo. Declaren los métodos como synchronized (o usen un mismo candado).
 * - confirmar y liberar deben ser IDEMPOTENTES: llamarlos dos veces, o sobre una reserva que
 *   no existe (por ejemplo, porque ya se liberó o venció), NO debe fallar ni descontar dos veces.
 */
public interface IInventarioLocal {

    /**
     * Aparta unidades para un pedido de otra sede (todavía no se venden).
     * Las unidades reservadas no se pueden vender ni reservar para otro pedido.
     *
     * @return RespuestaReserva.ok(null) si se pudo, o RespuestaReserva.rechazo(null, motivo) si no.
     *         Ejemplo de motivo: "stock insuficiente de B01 (hay 1, se piden 2)" o "el producto B01 no existe".
     *         La mensajería agrega el nombre de la sede al inicio del motivo.
     */
    RespuestaReserva reservar(String idPedido, String codigoProducto, int cantidad);

    /**
     * Descuenta definitivamente las unidades reservadas para ese pedido y producto.
     * Idempotente: si la reserva no existe (o ya se confirmó), no hace nada.
     *
     * @return el stock NUEVO del producto en esta sede (se difunde a todas las sedes).
     */
    int confirmar(String idPedido, String codigoProducto);

    /**
     * Devuelve al inventario las unidades reservadas para ese pedido y producto.
     * Idempotente: si la reserva no existe (o ya se liberó), no hace nada.
     */
    void liberar(String idPedido, String codigoProducto);
}
