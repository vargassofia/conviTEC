package cr.ac.tec.convitec.estadisticas;

/**
 * El producto que generó el MAYOR monto total en ventas de la sede (unidades × precio).
 * Gana por monto, no por cantidad de unidades.
 *
 * Tiene los mismos datos que ResumenProducto: es el primer lugar del ranking.
 */
public final class ProductoEstrella extends ResumenProducto {

    public ProductoEstrella(String codigoProducto, String nombreProducto, int unidadesVendidas, double montoTotal) {
        super(codigoProducto, nombreProducto, unidadesVendidas, montoTotal);
    }

    /** Ejemplo: "Producto estrella: Café (C01): 3 u., ₡4500.00". */
    @Override
    public String toString() {
        return "Producto estrella: " + super.toString();
    }
}
