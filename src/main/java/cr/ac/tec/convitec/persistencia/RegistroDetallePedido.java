package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.modelo.DetallePedido;

/** Registro JSON de una línea de pedido. */
public final class RegistroDetallePedido {
    public int version = 1;
    public String codigo;
    public String nombre;
    public int cantidad;
    public double precioUnitario;
    public String sedeSurtidora;

    public RegistroDetallePedido() {
    }

    public static RegistroDetallePedido desdeModelo(DetallePedido detalle) {
        if (detalle == null) {
            throw new PersistenciaException("No se puede persistir un detalle de pedido null");
        }
        RegistroDetallePedido registro = new RegistroDetallePedido();
        registro.codigo = detalle.getCodigoProducto();
        registro.nombre = detalle.getNombre();
        registro.cantidad = detalle.getCantidad();
        registro.precioUnitario = detalle.getPrecioUnitario();
        registro.sedeSurtidora = detalle.getSedeSurtidora();
        return registro;
    }

    public DetallePedido haciaModelo() {
        if (version != 1) {
            throw new PersistenciaException("Versión de detalle de pedido desconocida: " + version);
        }
        try {
            return new DetallePedido(codigo, nombre, cantidad, precioUnitario, sedeSurtidora);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Detalle de pedido inconsistente: " + e.getMessage(), e);
        }
    }
}
