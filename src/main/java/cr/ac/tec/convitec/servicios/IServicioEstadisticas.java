package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.modelo.Venta;

public interface IServicioEstadisticas {
    Venta[] historialVentas();
    Producto productoEstrella();
    Pedido[] peoresPedidos(int cantidad);
}
