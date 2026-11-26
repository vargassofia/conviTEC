package cr.ac.tec.convitec.catalogo;

/** Datos serializables para guardar y reconstruir una reserva. */
public final class ReservaPersistida {

    private final String idPedido;
    private final String codigoProducto;
    private final int cantidad;

    /** Crea un registro persistible de reserva. */
    public ReservaPersistida(String idPedido, String codigoProducto, int cantidad) {
        this.idPedido = idPedido;
        this.codigoProducto = codigoProducto;
        this.cantidad = cantidad;
    }

    /** Devuelve el identificador del pedido. */
    public String getIdPedido() {
        return idPedido;
    }

    /** Devuelve el código del producto. */
    public String getCodigoProducto() {
        return codigoProducto;
    }

    /** Devuelve las unidades reservadas. */
    public int getCantidad() {
        return cantidad;
    }
}
