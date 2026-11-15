package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.modelo.Venta;

/**
 * Contrato para guardar las ventas (lo implementa la Persona 2 con archivos locales).
 *
 * Mientras tanto existe cr.ac.tec.convitec.entregas.RepositorioVentasMemoria,
 * que guarda en memoria (sirve para pruebas y para probar la interfaz).
 */
public interface IRepositorioVentas {

    /**
     * Guarda una venta nueva (en el archivo de ventas de la sede).
     * El servicio de entregas ya revisa que el pedido no tenga venta antes de llamar a este método.
     */
    void guardar(Venta venta);

    /**
     * Todas las ventas guardadas, en el orden en que se guardaron.
     * Si no hay ventas, devuelvan un arreglo vacío (new Venta[0]), nunca null.
     * Se usa para el historial de ventas y las estadísticas.
     */
    Venta[] listar();

    /**
     * La venta del pedido indicado, o null si ese pedido todavía no tiene venta.
     * Se usa para no registrar dos veces la misma entrega.
     */
    Venta buscarPorIdPedido(String idPedido);
}
