package cr.ac.tec.convitec.pedidos;

import cr.ac.tec.convitec.modelo.Pedido;

/** Resultado de registrar un pedido aceptado o cancelado. */
public final class ResultadoPedido {

    private final boolean aceptado;
    private final Pedido pedido;
    private final String motivo;

    /** Crea el resultado con una copia del pedido. */
    public ResultadoPedido(boolean aceptado, Pedido pedido, String motivo) {
        if (pedido == null) {
            throw new IllegalArgumentException("El resultado debe incluir un pedido");
        }
        this.aceptado = aceptado;
        this.pedido = pedido.copia();
        this.motivo = motivo;
    }

    /** Indica si el pedido fue confirmado. */
    public boolean isAceptado() {
        return aceptado;
    }

    /** Devuelve una copia del pedido resultante. */
    public Pedido getPedido() {
        return pedido.copia();
    }

    /** Devuelve el motivo del rechazo, o null si fue aceptado. */
    public String getMotivo() {
        return motivo;
    }
}
