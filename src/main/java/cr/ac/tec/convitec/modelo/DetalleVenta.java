package cr.ac.tec.convitec.modelo;

/**
 * Una línea de una venta: qué producto se vendió, cuántas unidades y a qué precio.
 *
 * El subtotal (cantidad × precioUnitario) se calcula aquí, no se recibe.
 * Es inmutable y valida sus datos en el constructor.
 */
public final class DetalleVenta {

    private final String codigoProducto;
    private final String nombreProducto;
    private final int cantidad;
    private final double precioUnitario;
    private final double subtotal;

    /**
     * @throws IllegalArgumentException si el código o el nombre están vacíos,
     *                                  la cantidad es <= 0 o el precio es negativo.
     */
    public DetalleVenta(String codigoProducto, String nombreProducto, int cantidad, double precioUnitario) {
        if (codigoProducto == null || codigoProducto.isBlank()) {
            throw new IllegalArgumentException("El detalle de venta no tiene código de producto.");
        }
        if (nombreProducto == null || nombreProducto.isBlank()) {
            throw new IllegalArgumentException("El producto '" + codigoProducto + "' no tiene nombre.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad del producto '" + codigoProducto
                    + "' debe ser mayor que 0 (llegó " + cantidad + ").");
        }
        if (Double.isNaN(precioUnitario) || Double.isInfinite(precioUnitario) || precioUnitario < 0) {
            throw new IllegalArgumentException("El precio unitario del producto '" + codigoProducto
                    + "' no puede ser negativo ni inválido (llegó " + precioUnitario + ").");
        }
        this.codigoProducto = codigoProducto.trim();
        this.nombreProducto = nombreProducto.trim();
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = cantidad * precioUnitario;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getPrecioUnitario() {
        return precioUnitario;
    }

    /** cantidad × precioUnitario. */
    public double getSubtotal() {
        return subtotal;
    }

    /** Ejemplo: "2 x Café (C01) a ₡1500.00 = ₡3000.00". */
    @Override
    public String toString() {
        return String.format("%d x %s (%s) a ₡%.2f = ₡%.2f",
                cantidad, nombreProducto, codigoProducto, precioUnitario, subtotal);
    }
}
