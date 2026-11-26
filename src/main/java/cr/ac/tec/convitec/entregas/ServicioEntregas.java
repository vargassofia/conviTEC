package cr.ac.tec.convitec.entregas;

import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.servicios.IGestorPedidos;
import cr.ac.tec.convitec.servicios.IRepositorioVentas;

import java.time.LocalDateTime;

/**
 * Registra las entregas de una sede: cada pedido entregado se convierte en una Venta.
 *
 * Por cada pedido de una parada:
 * 1. Pide el detalle (productos, cantidades, precios) al gestor de pedidos.
 * 2. Crea la Venta: idVenta = "V-" + idPedido, destino = destino de la parada,
 *    tiempoTraslado = tiempo acumulado de la parada (desde la sede).
 * 3. La guarda en el repositorio de ventas.
 * 4. Marca el pedido como entregado.
 *
 * Una parada es "todo o nada": primero se validan y crean TODAS las ventas de la parada,
 * y solo si todas están bien se guardan. Si un pedido falla, no se registra nada de esa parada.
 */
public class ServicioEntregas {

    private static final String PREFIJO_VENTA = "V-";

    private final String sede;
    private final IGestorPedidos gestor;
    private final IRepositorioVentas repositorio;

    /**
     * @throws IllegalArgumentException si la sede está vacía o el gestor o el repositorio son nulos.
     */
    public ServicioEntregas(String sede, IGestorPedidos gestor, IRepositorioVentas repositorio) {
        if (sede == null || sede.isBlank()) {
            throw new IllegalArgumentException("El servicio de entregas necesita la sede.");
        }
        if (gestor == null) {
            throw new IllegalArgumentException("El servicio de entregas necesita un gestor de pedidos.");
        }
        if (repositorio == null) {
            throw new IllegalArgumentException("El servicio de entregas necesita un repositorio de ventas.");
        }
        this.sede = sede.trim();
        this.gestor = gestor;
        this.repositorio = repositorio;
    }

    public String getSede() {
        return sede;
    }

    /**
     * Registra los pedidos de UNA parada (para el botón paso a paso de la interfaz).
     *
     * @return las ventas creadas, en el mismo orden que los ids de la parada.
     * @throws IllegalArgumentException si la parada o la fecha son nulas, o la hora de salida es inválida.
     * @throws IllegalStateException    si un pedido no existe, no tiene productos o ya tiene venta.
     *                                  En ese caso no se registra nada de esta parada.
     */
    public Venta[] registrarParada(ParadaEntrega parada, String horaSalida, LocalDateTime fechaHora) {
        if (parada == null) {
            throw new IllegalArgumentException("La parada a registrar es nula.");
        }
        if (fechaHora == null) {
            throw new IllegalArgumentException("La fecha de la entrega es nula.");
        }
        if (!SalidasDiarias.esHoraSalidaValida(horaSalida)) {
            throw new IllegalArgumentException("Hora de salida inválida: '" + horaSalida + "'.");
        }

        String[] ids = parada.getIdsPedidos();

        // 1) Validar y crear todas las ventas, SIN guardar nada todavía
        Venta[] ventas = new Venta[ids.length];
        for (int i = 0; i < ids.length; i++) {
            String idPedido = ids[i];
            for (int j = 0; j < i; j++) {
                if (ids[j].equals(idPedido)) {
                    throw new IllegalStateException("El pedido '" + idPedido + "' aparece dos veces en la parada.");
                }
            }
            if (repositorio.buscarPorIdPedido(idPedido) != null) {
                throw new IllegalStateException("El pedido '" + idPedido
                        + "' ya tiene una venta registrada: no se duplica.");
            }
            DetalleVenta[] detalle = gestor.obtenerDetalle(idPedido);
            if (detalle == null) {
                throw new IllegalStateException("El pedido '" + idPedido + "' no existe en el gestor de pedidos.");
            }
            if (detalle.length == 0) {
                throw new IllegalStateException("El pedido '" + idPedido + "' no tiene productos.");
            }
            try {
                ventas[i] = new Venta(PREFIJO_VENTA + idPedido, idPedido, sede, parada.getDestino(),
                        fechaHora, horaSalida, detalle, parada.getTiempoAcumulado());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("El pedido '" + idPedido + "' tiene datos inválidos: "
                        + e.getMessage(), e);
            }
        }

        // 2) Todo está bien: guardar y marcar como entregado
        for (int i = 0; i < ventas.length; i++) {
            repositorio.guardar(ventas[i]);
            gestor.marcarEntregado(ventas[i].getIdPedido(), fechaHora);
        }
        return ventas;
    }

    /**
     * Registra todas las paradas de un recorrido, en orden, reutilizando registrarParada.
     *
     * Cada parada es "todo o nada" por separado: si falla la parada 3, las paradas 1 y 2
     * ya quedaron registradas y se lanza la excepción.
     *
     * @return todas las ventas creadas (vacío si el recorrido no tiene paradas).
     * @throws IllegalArgumentException si el recorrido o la fecha son nulos.
     * @throws IllegalStateException    si algún pedido no se puede registrar (ver registrarParada).
     */
    public Venta[] registrarRecorrido(RecorridoEntrega recorrido, LocalDateTime fechaHora) {
        if (recorrido == null) {
            throw new IllegalArgumentException("El recorrido a registrar es nulo.");
        }
        if (fechaHora == null) {
            throw new IllegalArgumentException("La fecha de la entrega es nula.");
        }
        ParadaEntrega[] paradas = recorrido.getParadas();

        int total = 0;
        for (int i = 0; i < paradas.length; i++) {
            total += paradas[i].getCantidadPedidos();
        }

        Venta[] todas = new Venta[total];
        int pos = 0;
        for (int i = 0; i < paradas.length; i++) {
            Venta[] deParada = registrarParada(paradas[i], recorrido.getHoraSalida(), fechaHora);
            for (int j = 0; j < deParada.length; j++) {
                todas[pos] = deParada[j];
                pos++;
            }
        }
        return todas;
    }
}
