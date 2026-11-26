package cr.ac.tec.convitec.catalogo;

/** Vista para la API de un producto en el inventario de esta sede. */
public final class ExistenciaInventario {

    private final String codigoProducto;
    private final String nombreProducto;
    private final double precio;
    private final int cantidadDisponible;
    private final int cantidadReservada;

    /** Crea una vista con los datos del producto y sus cantidades locales. */
    public ExistenciaInventario(String codigoProducto, String nombreProducto, double precio,
                                int cantidadDisponible, int cantidadReservada) {
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.precio = precio;
        this.cantidadDisponible = cantidadDisponible;
        this.cantidadReservada = cantidadReservada;
    }

    /** Devuelve el código del producto. */
    public String getCodigoProducto() {
        return codigoProducto;
    }

    /** Devuelve el nombre del producto. */
    public String getNombreProducto() {
        return nombreProducto;
    }

    /** Devuelve el precio del catálogo. */
    public double getPrecio() {
        return precio;
    }

    /** Devuelve las unidades disponibles localmente. */
    public int getCantidadDisponible() {
        return cantidadDisponible;
    }

    /** Devuelve las unidades reservadas localmente. */
    public int getCantidadReservada() {
        return cantidadReservada;
    }
}
