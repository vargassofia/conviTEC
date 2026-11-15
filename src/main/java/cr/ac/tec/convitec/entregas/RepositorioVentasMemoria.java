package cr.ac.tec.convitec.entregas;

import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.servicios.IRepositorioVentas;

/**
 * Repositorio de ventas en memoria (no guarda en archivos).
 *
 * Sirve para las pruebas y para probar la interfaz antes de que exista la versión
 * con archivos de la Persona 2. Usa un arreglo que crece duplicando su tamaño (sin colecciones).
 */
public class RepositorioVentasMemoria implements IRepositorioVentas {

    private static final int CAPACIDAD_INICIAL = 8;

    private Venta[] ventas;
    private int cantidad;

    public RepositorioVentasMemoria() {
        this(CAPACIDAD_INICIAL);
    }

    public RepositorioVentasMemoria(int capacidadInicial) {
        ventas = new Venta[capacidadInicial > 0 ? capacidadInicial : CAPACIDAD_INICIAL];
        cantidad = 0;
    }

    /**
     * @throws IllegalArgumentException si la venta es nula.
     * @throws IllegalStateException    si ese pedido ya tiene una venta guardada.
     */
    @Override
    public void guardar(Venta venta) {
        if (venta == null) {
            throw new IllegalArgumentException("No se puede guardar una venta nula.");
        }
        if (buscarPorIdPedido(venta.getIdPedido()) != null) {
            throw new IllegalStateException("El pedido '" + venta.getIdPedido() + "' ya tiene una venta guardada.");
        }
        if (cantidad == ventas.length) {
            crecer();
        }
        ventas[cantidad] = venta;
        cantidad++;
    }

    /** Copia de las ventas en el orden en que se guardaron (vacío si no hay). */
    @Override
    public Venta[] listar() {
        Venta[] copia = new Venta[cantidad];
        for (int i = 0; i < cantidad; i++) {
            copia[i] = ventas[i];
        }
        return copia;
    }

    /** Búsqueda lineal por idPedido; null si no existe. */
    @Override
    public Venta buscarPorIdPedido(String idPedido) {
        if (idPedido == null) {
            return null;
        }
        String buscado = idPedido.trim();
        for (int i = 0; i < cantidad; i++) {
            if (ventas[i].getIdPedido().equals(buscado)) {
                return ventas[i];
            }
        }
        return null;
    }

    public int getCantidad() {
        return cantidad;
    }

    private void crecer() {
        Venta[] nuevas = new Venta[ventas.length * 2];
        for (int i = 0; i < cantidad; i++) {
            nuevas[i] = ventas[i];
        }
        ventas = nuevas;
    }
}
