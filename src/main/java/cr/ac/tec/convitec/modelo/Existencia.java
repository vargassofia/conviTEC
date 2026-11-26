package cr.ac.tec.convitec.modelo;

/**
 * Cantidades disponible y reservada de un producto en una sede.
 */
public class Existencia {

    private final String codigoProducto;
    private int cantidadDisponible;
    private int cantidadReservada;

    /** Crea una existencia sin unidades reservadas. */
    public Existencia(String codigoProducto, int cantidadDisponible) {
        this(codigoProducto, cantidadDisponible, 0);
    }

    /** Crea una existencia con cantidades disponible y reservada. */
    public Existencia(String codigoProducto, int cantidadDisponible, int cantidadReservada) {
        validarCodigoProducto(codigoProducto);
        validarCantidadNoNegativa(cantidadDisponible, "La cantidad disponible");
        validarCantidadNoNegativa(cantidadReservada, "La cantidad reservada");
        validarTotalRepresentable(cantidadDisponible, cantidadReservada);

        this.codigoProducto = codigoProducto;
        this.cantidadDisponible = cantidadDisponible;
        this.cantidadReservada = cantidadReservada;
    }

    /** Mueve unidades disponibles a reservadas, o devuelve false si no alcanza el stock. */
    public boolean reservar(int cantidad) {
        validarCantidadPositiva(cantidad);
        if (cantidad > cantidadDisponible) {
            return false;
        }
        cantidadDisponible -= cantidad;
        cantidadReservada += cantidad;
        return true;
    }

    /** Devuelve unidades reservadas al inventario disponible. */
    public void liberar(int cantidad) {
        validarCantidadPositiva(cantidad);
        validarCantidadReservada(cantidad);

        cantidadReservada -= cantidad;
        cantidadDisponible += cantidad;
    }

    /** Descuenta definitivamente unidades reservadas. */
    public void confirmar(int cantidad) {
        validarCantidadPositiva(cantidad);
        validarCantidadReservada(cantidad);

        cantidadReservada -= cantidad;
    }

    /** Agrega unidades al inventario disponible. */
    public void agregar(int cantidad) {
        validarCantidadPositiva(cantidad);
        if (cantidad > Integer.MAX_VALUE - cantidadDisponible) {
            throw new IllegalArgumentException("La cantidad disponible excedería el máximo de int");
        }
        validarTotalRepresentable(cantidadDisponible + cantidad, cantidadReservada);
        cantidadDisponible += cantidad;
    }

    /** Descuenta unidades del inventario disponible. */
    public void descontar(int cantidad) {
        validarCantidadPositiva(cantidad);
        if (cantidad > cantidadDisponible) {
            throw new IllegalArgumentException("No hay suficientes unidades disponibles");
        }
        cantidadDisponible -= cantidad;
    }

    /** Devuelve la suma de las unidades disponibles y reservadas. */
    public int getTotal() {
        return cantidadDisponible + cantidadReservada;
    }

    /** Devuelve el código del producto. */
    public String getCodigoProducto() {
        return codigoProducto;
    }

    /** Devuelve las unidades disponibles. */
    public int getCantidadDisponible() {
        return cantidadDisponible;
    }

    /** Devuelve las unidades reservadas. */
    public int getCantidadReservada() {
        return cantidadReservada;
    }

    /** Crea una copia independiente de esta existencia. */
    public Existencia copia() {
        return new Existencia(codigoProducto, cantidadDisponible, cantidadReservada);
    }

    /** Compara el código y las cantidades de esta existencia con otro objeto. */
    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Existencia otra)) {
            return false;
        }
        return cantidadDisponible == otra.cantidadDisponible
                && cantidadReservada == otra.cantidadReservada
                && codigoProducto.equals(otra.codigoProducto);
    }

    /** Devuelve un código hash coherente con {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        int resultado = codigoProducto.hashCode();
        resultado = 31 * resultado + cantidadDisponible;
        resultado = 31 * resultado + cantidadReservada;
        return resultado;
    }

    /** Devuelve una representación legible del código y las cantidades. */
    @Override
    public String toString() {
        return "Existencia{codigoProducto='" + codigoProducto
                + "', cantidadDisponible=" + cantidadDisponible
                + ", cantidadReservada=" + cantidadReservada + "}";
    }

    private void validarCantidadPositiva(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
    }

    private void validarCantidadNoNegativa(int cantidad, String nombreCantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException(nombreCantidad + " no puede ser negativa");
        }
    }

    private void validarCantidadReservada(int cantidad) {
        if (cantidad > cantidadReservada) {
            throw new IllegalArgumentException("No hay suficientes unidades reservadas");
        }
    }

    private void validarTotalRepresentable(int disponible, int reservado) {
        if ((long) disponible + reservado > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El total de unidades excedería el máximo de int");
        }
    }

    private static void validarCodigoProducto(String codigoProducto) {
        if (codigoProducto == null || codigoProducto.trim().isEmpty()) {
            throw new IllegalArgumentException("El código del producto no puede ser null ni estar vacío");
        }
    }
}
