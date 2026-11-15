package cr.ac.tec.convitec.servicios;

/**
 * Copia local del catálogo global: cuánto stock tiene cada sede de cada producto
 * (lo implementa la Persona 2).
 *
 * La mensajería lo llama cuando llega una ACTUALIZACION_STOCK de otra sede.
 * Debe ser thread-safe (synchronized), porque se llama desde el hilo de RabbitMQ.
 */
public interface ICatalogoGlobal {

    /**
     * Guarda que la sede codigoSede ahora tiene cantidadNueva unidades del producto.
     * Si el producto no estaba en el catálogo, agréguenlo.
     */
    void actualizarStock(String codigoSede, String codigoProducto, int cantidadNueva);
}
