package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.modelo.Producto;

/** Producto del catálogo global junto con el stock conocido por sede. */
public final class ProductoCatalogo {

    private final Producto producto;
    private final DisponibilidadSede[] disponibilidades;

    /** Crea una fila del catálogo con una copia del arreglo de disponibilidades. */
    public ProductoCatalogo(Producto producto, DisponibilidadSede[] disponibilidades) {
        this.producto = producto;
        this.disponibilidades = copiar(disponibilidades);
    }

    /** Devuelve el producto del catálogo. */
    public Producto getProducto() {
        return producto;
    }

    /** Devuelve una copia de las disponibilidades conocidas. */
    public DisponibilidadSede[] getDisponibilidades() {
        return copiar(disponibilidades);
    }

    private static DisponibilidadSede[] copiar(DisponibilidadSede[] origen) {
        if (origen == null) {
            return new DisponibilidadSede[0];
        }
        DisponibilidadSede[] copia = new DisponibilidadSede[origen.length];
        System.arraycopy(origen, 0, copia, 0, origen.length);
        return copia;
    }
}
