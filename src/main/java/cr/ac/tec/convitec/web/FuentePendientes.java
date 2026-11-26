package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.rutas.SolicitudEntrega;

/**
 * De dónde saca la pantalla de Entregas los pedidos que tiene que repartir el vehículo.
 *
 * Hoy la implementa PendientesDemo (pedidos de prueba fijos). Cuando exista el módulo de pedidos
 * de la Persona 2, su gestor de pedidos la implementa devolviendo los pedidos ACEPTADOS y todavía
 * no entregados de esa salida, convertidos a SolicitudEntrega(id, destino).
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
