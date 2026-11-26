package cr.ac.tec.convitec.modelo;

/** Línea de pedido asignada a la sede que aporta sus unidades. */
public final class DetallePedido {

    private final String codigoProducto;
    private final String nombre;
    private final int cantidad;
    private final double precioUnitario;
    private final String sedeSurtidora;

    /** Crea un detalle validando el producto, cantidad, precio y sede proveedora. */
    public DetallePedido(String codigoProducto, String nombre, int cantidad,
                         double precioUnitario, String sedeSurtidora) {
        this.codigoProducto = validarTexto(codigoProducto, "El código del producto");
        this.nombre = validarTexto(nombre, "El nombre del producto");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad del detalle debe ser mayor que cero");
        }
        if (Double.isNaN(precioUnitario) || Double.isInfinite(precioUnitario) || precioUnitario <= 0) {
            throw new IllegalArgumentException("El precio unitario debe ser finito y mayor que cero");
        }
        this.sedeSurtidora = validarTexto(sedeSurtidora, "La sede que surte");
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    /** Devuelve el código del producto. */
    public String getCodigoProducto() {
        return codigoProducto;
    }

    /** Devuelve el nombre del producto. */
    public String getNombre() {
        return nombre;
    }

    /** Devuelve la cantidad aportada por la sede. */
    public int getCantidad() {
        return cantidad;
    }

    /** Devuelve el precio unitario fijado al registrar el pedido. */
    public double getPrecioUnitario() {
        return precioUnitario;
    }

    /** Devuelve el código de la sede que surte las unidades. */
    public String getSedeSurtidora() {
        return sedeSurtidora;
    }

    /** Devuelve el subtotal de la línea. */
    public double getSubtotal() {
        return cantidad * precioUnitario;
    }

    private static String validarTexto(String texto, String campo) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException(campo + " no puede ser null ni vacío");
        }
        return texto.trim();
    }
}
