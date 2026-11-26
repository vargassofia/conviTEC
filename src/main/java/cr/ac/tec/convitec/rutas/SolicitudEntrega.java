package cr.ac.tec.convitec.rutas;

/**
 * Entrada del planificador: un pedido pendiente de entregar.
 *
 * Solo tiene lo que el planificador necesita (id y destino), así el módulo de rutas
 * no depende de la clase Pedido. El módulo de pedidos convierte sus pedidos
 * pendientes a un arreglo SolicitudEntrega[].
 */
public final class SolicitudEntrega {

    private final String idPedido;
    private final String destino;

    public SolicitudEntrega(String idPedido, String destino) {
        this.idPedido = idPedido;
        this.destino = destino;
    }

    public String getIdPedido() {
        return idPedido;
    }

    public String getDestino() {
        return destino;
    }

    @Override
    public String toString() {
        return idPedido + " -> " + destino;
    }
}
