package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.ReservaPersistida;

/** Registro JSON de una reserva local. */
public final class RegistroReserva {
    public int version = 1;
    public String idPedido;
    public String codigo;
    public int cantidad;

    public RegistroReserva() {
    }

    public static RegistroReserva desdeModelo(ReservaPersistida reserva) {
        if (reserva == null) {
            throw new PersistenciaException("No se puede persistir una reserva null");
        }
        RegistroReserva registro = new RegistroReserva();
        registro.idPedido = reserva.getIdPedido();
        registro.codigo = reserva.getCodigoProducto();
        registro.cantidad = reserva.getCantidad();
        return registro;
    }

    public ReservaPersistida haciaModelo() {
        if (version != 1 || idPedido == null || idPedido.isBlank() || codigo == null || codigo.isBlank()
                || cantidad <= 0) {
            throw new PersistenciaException("Registro de reserva inconsistente");
        }
        return new ReservaPersistida(idPedido, codigo, cantidad);
    }
}
