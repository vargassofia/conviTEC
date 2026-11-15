public interface IServicioRutas {
    boolean existeRuta(String origen, String destino);
    ResultadoRuta caminoMasCorto(String origen, String destino); // tiempo + camino
    ParadaEntrega calcularSiguienteEntrega();   // paso a paso
    RecorridoEntrega planificarRecorridoCompleto();
}

// servicios/IMensajeria.java
public interface IMensajeria {
    boolean solicitarReserva(String sedeDestino, String idPedido, String codigoProducto, int cantidad);
    void confirmarReserva(String sedeDestino, String idPedido);
    void liberarReserva(String sedeDestino, String idPedido);
    void publicarActualizacionStock(String codigoProducto, int cantidadNueva);
}

// servicios/IServicioEstadisticas.java
public interface IServicioEstadisticas {
    Venta[] historialVentas();
    Producto productoEstrella();
    Pedido[] peoresPedidos(int cantidad);
}