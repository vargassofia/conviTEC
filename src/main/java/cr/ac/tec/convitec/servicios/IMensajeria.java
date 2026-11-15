package cr.ac.tec.convitec.servicios;

public interface IMensajeria {
    boolean solicitarReserva(String sedeDestino, String idPedido, String codigoProducto, int cantidad);
    void confirmarReserva(String sedeDestino, String idPedido);
    void liberarReserva(String sedeDestino, String idPedido);
    void publicarActualizacionStock(String codigoProducto, int cantidadNueva);
}
