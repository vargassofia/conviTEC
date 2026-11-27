package cr.ac.tec.convitec.persistencia;

/** Liberación remota que debe enviarse al recuperar una sede. */
public final class LiberacionPendiente {
    private final String codigoSede;
    private final String idPedido;
    private final String codigoProducto;

    public LiberacionPendiente(String codigoSede, String idPedido, String codigoProducto) {
        this.codigoSede = codigoSede;
        this.idPedido = idPedido;
        this.codigoProducto = codigoProducto;
    }

    public String getCodigoSede() {
        return codigoSede;
    }

    public String getIdPedido() {
        return idPedido;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }
}
