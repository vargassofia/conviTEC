package cr.ac.tec.convitec.estadisticas;

/**
 * Resumen de cuánto se vendió de un producto en la sede (para el ranking de productos).
 *
 * - unidadesVendidas: suma de las cantidades en todas las ventas.
 * - montoTotal: suma de los subtotales (unidades × precio) en todas las ventas.
 *
 * Es inmutable.
 */
public class ResumenProducto {

    private final String codigoProducto;
    private final String nombreProducto;
    private final int unidadesVendidas;
    private final double montoTotal;

    public ResumenProducto(String codigoProducto, String nombreProducto, int unidadesVendidas, double montoTotal) {
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.unidadesVendidas = unidadesVendidas;
        this.montoTotal = montoTotal;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public int getUnidadesVendidas() {
        return unidadesVendidas;
    }

    public double getMontoTotal() {
        return montoTotal;
    }

    /** Ejemplo: "Café (C01): 3 u., ₡4500.00". */
    @Override
    public String toString() {
        return String.format("%s (%s): %d u., ₡%.2f", nombreProducto, codigoProducto, unidadesVendidas, montoTotal);
    }
}
