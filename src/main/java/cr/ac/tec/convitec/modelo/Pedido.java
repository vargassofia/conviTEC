package cr.ac.tec.convitec.modelo;

import java.time.LocalDateTime;

/** Pedido de una sede con líneas asignadas a las sedes que lo surten. */
public final class Pedido {

    private final String id;
    private final String sedeOrigen;
    private final String destino;
    private final LocalDateTime fechaCreacion;
    private EstadoPedido estado;
    private String motivoCancelacion;
    private final DetallePedido[] detalles;
    private final String[] sedesParticipantes;
    private LocalDateTime fechaEntrega;

    /** Crea un pedido con su estado y copias defensivas de los arreglos. */
    public Pedido(String id, String sedeOrigen, String destino, LocalDateTime fechaCreacion,
                  EstadoPedido estado, String motivoCancelacion, DetallePedido[] detalles,
                  String[] sedesParticipantes) {
        this.id = validarTexto(id, "El id del pedido");
        this.sedeOrigen = validarTexto(sedeOrigen, "La sede de origen");
        this.destino = validarTexto(destino, "El destino");
        if (fechaCreacion == null || estado == null || detalles == null || sedesParticipantes == null) {
            throw new IllegalArgumentException("Fecha, estado y arreglos del pedido no pueden ser null");
        }
        this.fechaCreacion = fechaCreacion;
        this.estado = estado;
        this.motivoCancelacion = motivoCancelacion;
        this.detalles = copiarDetalles(detalles);
        this.sedesParticipantes = copiarSedes(sedesParticipantes);
    }

    /** Devuelve el identificador único del pedido. */
    public String getId() {
        return id;
    }

    /** Devuelve el código de la sede de origen. */
    public String getSedeOrigen() {
        return sedeOrigen;
    }

    /** Devuelve el destino de entrega. */
    public String getDestino() {
        return destino;
    }

    /** Devuelve la fecha y hora de creación. */
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** Devuelve el estado actual. */
    public EstadoPedido getEstado() {
        return estado;
    }

    /** Devuelve el motivo de cancelación, o null si no fue cancelado. */
    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    /** Devuelve una copia de los detalles del pedido. */
    public DetallePedido[] getDetalles() {
        return copiarDetalles(detalles);
    }

    /** Devuelve una copia de los códigos de sede participantes. */
    public String[] getSedesParticipantes() {
        String[] copia = new String[sedesParticipantes.length];
        System.arraycopy(sedesParticipantes, 0, copia, 0, sedesParticipantes.length);
        return copia;
    }

    /** Devuelve la suma de los subtotales del pedido. */
    public double getMontoTotal() {
        double total = 0;
        for (DetallePedido detalle : detalles) {
            total += detalle.getSubtotal();
        }
        return total;
    }

    /** Devuelve la fecha de entrega, o null mientras no se haya entregado. */
    public LocalDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    /** Cambia el estado del pedido a confirmado. */
    public void confirmar() {
        if (estado == EstadoPedido.EN_PROCESO) {
            estado = EstadoPedido.CONFIRMADO;
        }
    }

    /** Cancela el pedido y guarda el motivo. */
    public void cancelar(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de cancelación no puede ser vacío");
        }
        if (estado == EstadoPedido.EN_PROCESO || estado == EstadoPedido.CONFIRMADO) {
            estado = EstadoPedido.CANCELADO;
            motivoCancelacion = motivo.trim();
        }
    }

    /** Marca como entregado un pedido confirmado. */
    public void marcarEntregado(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            throw new IllegalArgumentException("La fecha de entrega no puede ser null");
        }
        if (estado == EstadoPedido.CONFIRMADO) {
            estado = EstadoPedido.ENTREGADO;
            fechaEntrega = fechaHora;
        }
    }

    /** Devuelve una copia independiente del pedido y de sus arreglos. */
    public Pedido copia() {
        Pedido copia = new Pedido(id, sedeOrigen, destino, fechaCreacion, estado, motivoCancelacion,
                detalles, sedesParticipantes);
        copia.fechaEntrega = fechaEntrega;
        return copia;
    }

    private static String validarTexto(String texto, String campo) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException(campo + " no puede ser null ni vacío");
        }
        return texto.trim();
    }

    private static DetallePedido[] copiarDetalles(DetallePedido[] origen) {
        DetallePedido[] copia = new DetallePedido[origen.length];
        System.arraycopy(origen, 0, copia, 0, origen.length);
        for (DetallePedido detalle : copia) {
            if (detalle == null) {
                throw new IllegalArgumentException("El pedido no puede contener detalles null");
            }
        }
        return copia;
    }

    private static String[] copiarSedes(String[] origen) {
        String[] copia = new String[origen.length];
        for (int i = 0; i < origen.length; i++) {
            copia[i] = validarTexto(origen[i], "La sede participante");
        }
        return copia;
    }
}
