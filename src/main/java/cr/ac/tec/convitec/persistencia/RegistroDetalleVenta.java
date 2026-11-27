package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.modelo.DetalleVenta;

/** Registro JSON de una línea de venta. */
public final class RegistroDetalleVenta {
    public int version = 1;
    public String codigo;
    public String nombre;
    public int cantidad;
    public double precioUnitario;

    public RegistroDetalleVenta() {
    }

    public static RegistroDetalleVenta desdeModelo(DetalleVenta detalle) {
        if (detalle == null) {
            throw new PersistenciaException("No se puede persistir un detalle de venta null");
        }
        RegistroDetalleVenta registro = new RegistroDetalleVenta();
        registro.codigo = detalle.getCodigoProducto();
        registro.nombre = detalle.getNombreProducto();
        registro.cantidad = detalle.getCantidad();
        registro.precioUnitario = detalle.getPrecioUnitario();
        return registro;
    }

    public DetalleVenta haciaModelo() {
        if (version != 1) {
            throw new PersistenciaException("Versión de detalle de venta desconocida: " + version);
        }
        try {
            return new DetalleVenta(codigo, nombre, cantidad, precioUnitario);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Detalle de venta inconsistente: " + e.getMessage(), e);
        }
    }
}
