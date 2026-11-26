package cr.ac.tec.convitec.pedidos;

/** Código y cantidad solicitados para una línea de pedido. */
public final class SolicitudLinea {

    private final String codigoProducto;
    private final int cantidad;

    /** Crea una solicitud; el servicio valida código y cantidad al procesarla. */
    public SolicitudLinea(String codigoProducto, int cantidad) {
        this.codigoProducto = codigoProducto;
        this.cantidad = cantidad;
    }

    /** Devuelve el código solicitado. */
    public String getCodigoProducto() {
        return codigoProducto;
    }

    /** Devuelve la cantidad solicitada. */
    public int getCantidad() {
        return cantidad;
    }
}
