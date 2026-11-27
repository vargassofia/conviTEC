package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;

/** Fuente de entregas que consulta los pedidos confirmados de la sede. */
public final class PedidosFuentePendientes implements FuentePendientes {

    private final ServicioPedidos servicioPedidos;

    public PedidosFuentePendientes(ServicioPedidos servicioPedidos) {
        if (servicioPedidos == null) {
            throw new IllegalArgumentException("El servicio de pedidos no puede ser null");
        }
        this.servicioPedidos = servicioPedidos;
    }

    @Override
    public SolicitudEntrega[] pendientesDeSalida(String horaSalida) {
        if (!SalidasDiarias.esHoraSalidaValida(horaSalida)) {
            throw new IllegalArgumentException("Hora de salida inválida: " + horaSalida);
        }
        return servicioPedidos.construirSolicitudesEntrega();
    }
}
