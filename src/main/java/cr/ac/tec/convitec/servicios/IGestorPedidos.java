package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.modelo.DetalleVenta;

import java.time.LocalDateTime;

/**
 * Contrato con el módulo de pedidos (lo implementa la Persona 2).
 *
 * El módulo de entregas (cr.ac.tec.convitec.entregas.ServicioEntregas) lo usa para
 * convertir un pedido en venta cuando se entrega.
 */
public interface IGestorPedidos {

    /**
     * Devuelve los productos del pedido como detalles de venta: un DetalleVenta por producto,
     * con su código, nombre, cantidad y precio unitario (el precio al momento de la venta).
     *
     * Ejemplo para un pedido con 2 cafés de ₡1500 y 1 pan de ₡1000:
     *   return new DetalleVenta[]{
     *       new DetalleVenta("C01", "Café", 2, 1500),
     *       new DetalleVenta("P05", "Pan", 1, 1000)
     *   };
     *
     * @return el detalle del pedido, o null si el pedido no existe.
     *         No devuelvan un arreglo vacío para un pedido que existe: se toma como error.
     */
    DetalleVenta[] obtenerDetalle(String idPedido);

    /**
     * Cambia el estado del pedido a ENTREGADO y guarda la fecha y hora de la entrega.
     * Se llama DESPUÉS de guardar la venta.
     */
    void marcarEntregado(String idPedido, LocalDateTime fechaHora);
}
