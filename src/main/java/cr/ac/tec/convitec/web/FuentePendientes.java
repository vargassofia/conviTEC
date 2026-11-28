package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.rutas.SolicitudEntrega;

/**
 * De dónde saca la pantalla de Entregas los pedidos que tiene que repartir el vehículo.
 *
 * La implementa PedidosFuentePendientes, con los pedidos CONFIRMADOS y todavía no entregados del
 * servicio de pedidos, convertidos a SolicitudEntrega(id, destino).
 */
public interface FuentePendientes {

    /**
     * Pedidos pendientes que salen en esa salida.
     *
     * @param horaSalida "11:00" o "15:00"
     * @return los pendientes (vacío si no hay, nunca null)
     */
    SolicitudEntrega[] pendientesDeSalida(String horaSalida);

    /** true si los pedidos son de prueba: la interfaz lo avisa con una etiqueta. */
    default boolean esDemostracion() {
        return false;
    }
}
