package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Registro JSON de una venta, con fecha ISO-8601. */
public final class RegistroVenta {
    public int version = 1;
    public String idVenta;
    public String idPedido;
    public String sede;
    public String destino;
    public String fechaHora;
    public String horaSalida;
    public RegistroDetalleVenta[] detalles;
    public int tiempoTraslado;

    public RegistroVenta() {
    }

    public static RegistroVenta desdeModelo(Venta venta) {
        if (venta == null) {
            throw new PersistenciaException("No se puede persistir una venta null");
        }
        RegistroVenta registro = new RegistroVenta();
        registro.idVenta = venta.getIdVenta();
        registro.idPedido = venta.getIdPedido();
        registro.sede = venta.getSede();
        registro.destino = venta.getDestino();
        registro.fechaHora = venta.getFechaHora().toString();
        registro.horaSalida = venta.getHoraSalida();
        DetalleVenta[] detallesModelo = venta.getDetalles();
        registro.detalles = new RegistroDetalleVenta[detallesModelo.length];
        for (int i = 0; i < detallesModelo.length; i++) {
            registro.detalles[i] = RegistroDetalleVenta.desdeModelo(detallesModelo[i]);
        }
        registro.tiempoTraslado = venta.getTiempoTraslado();
        return registro;
    }

    public Venta haciaModelo() {
        if (version != 1 || detalles == null) {
            throw new PersistenciaException("Registro de venta inconsistente o versión desconocida");
        }
        try {
            DetalleVenta[] detallesModelo = new DetalleVenta[detalles.length];
            for (int i = 0; i < detalles.length; i++) {
                if (detalles[i] == null) {
                    throw new PersistenciaException("La venta contiene un detalle null");
                }
                detallesModelo[i] = detalles[i].haciaModelo();
            }
            return new Venta(idVenta, idPedido, sede, destino, LocalDateTime.parse(fechaHora),
                    horaSalida, detallesModelo, tiempoTraslado);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            if (e instanceof PersistenciaException persistenciaException) {
                throw persistenciaException;
            }
            throw new PersistenciaException("Venta inconsistente: " + e.getMessage(), e);
        }
    }
}
