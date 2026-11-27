package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.modelo.DetallePedido;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Pedido;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Registro JSON de un pedido, con fechas ISO-8601. */
public final class RegistroPedido {
    public int version = 1;
    public String id;
    public String sedeOrigen;
    public String destino;
    public String fechaCreacion;
    public String estado;
    public String motivoCancelacion;
    public RegistroDetallePedido[] detalles;
    public String[] sedesParticipantes;
    public String fechaEntrega;

    public RegistroPedido() {
    }

    public static RegistroPedido desdeModelo(Pedido pedido) {
        if (pedido == null) {
            throw new PersistenciaException("No se puede persistir un pedido null");
        }
        RegistroPedido registro = new RegistroPedido();
        registro.id = pedido.getId();
        registro.sedeOrigen = pedido.getSedeOrigen();
        registro.destino = pedido.getDestino();
        registro.fechaCreacion = pedido.getFechaCreacion().toString();
        registro.estado = pedido.getEstado().name();
        registro.motivoCancelacion = pedido.getMotivoCancelacion();
        DetallePedido[] detallesModelo = pedido.getDetalles();
        registro.detalles = new RegistroDetallePedido[detallesModelo.length];
        for (int i = 0; i < detallesModelo.length; i++) {
            registro.detalles[i] = RegistroDetallePedido.desdeModelo(detallesModelo[i]);
        }
        registro.sedesParticipantes = pedido.getSedesParticipantes();
        registro.fechaEntrega = pedido.getFechaEntrega() == null ? null : pedido.getFechaEntrega().toString();
        return registro;
    }

    public Pedido haciaModelo() {
        if (version != 1 || detalles == null || sedesParticipantes == null) {
            throw new PersistenciaException("Registro de pedido inconsistente o versión desconocida");
        }
        try {
            DetallePedido[] detallesModelo = new DetallePedido[detalles.length];
            for (int i = 0; i < detalles.length; i++) {
                if (detalles[i] == null) {
                    throw new PersistenciaException("El pedido contiene un detalle null");
                }
                detallesModelo[i] = detalles[i].haciaModelo();
            }
            Pedido pedido = new Pedido(id, sedeOrigen, destino, LocalDateTime.parse(fechaCreacion),
                    EstadoPedido.valueOf(estado), motivoCancelacion, detallesModelo, sedesParticipantes);
            if (pedido.getEstado() == EstadoPedido.ENTREGADO
                    && (fechaEntrega == null || fechaEntrega.isBlank())) {
                throw new PersistenciaException("Pedido entregado sin fecha de entrega");
            }
            if (pedido.getEstado() == EstadoPedido.CANCELADO
                    && (motivoCancelacion == null || motivoCancelacion.isBlank())) {
                throw new PersistenciaException("Pedido cancelado sin motivo");
            }
            if (fechaEntrega != null && !fechaEntrega.isBlank()) {
                pedido.restaurarFechaEntrega(LocalDateTime.parse(fechaEntrega));
            }
            return pedido;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new PersistenciaException("Pedido inconsistente: " + e.getMessage(), e);
        }
    }
}
