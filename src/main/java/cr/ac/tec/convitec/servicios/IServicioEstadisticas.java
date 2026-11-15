package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.estadisticas.ProductoEstrella;
import cr.ac.tec.convitec.estadisticas.ResumenProducto;
import cr.ac.tec.convitec.modelo.Venta;

import java.time.LocalDate;

/**
 * Estadísticas de UNA sede (cada sede solo ve sus propias ventas).
 * Implementación: cr.ac.tec.convitec.estadisticas.ServicioEstadisticas.
 */
public interface IServicioEstadisticas {

    /** Todas las ventas de la sede, de la más reciente a la más antigua (vacío si no hay). */
    Venta[] historialVentas();

    /**
     * Ventas entre dos fechas, ambos días incluidos, de la más reciente a la más antigua.
     * @throws IllegalArgumentException si alguna fecha es nula o desde es posterior a hasta.
     */
    Venta[] historialVentas(LocalDate desde, LocalDate hasta);

    /** Producto con mayor monto vendido (unidades × precio), o null si no hay ventas. */
    ProductoEstrella productoEstrella();

    /** Todos los productos vendidos, de mayor a menor monto (vacío si no hay ventas). */
    ResumenProducto[] rankingProductos();

    /**
     * Las N ventas con menor rendimiento (monto ÷ tiempo de traslado), de peor a mejor.
     * Si cantidad es mayor que el total, devuelve todas.
     * @throws IllegalArgumentException si cantidad <= 0.
     */
    Venta[] peoresPedidos(int cantidad);

    /** Suma de los montos de todas las ventas de la sede (0 si no hay). */
    double montoTotalVendido();
}
