package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.servicios.IMensajeria;

/** Fachada de inventario para la API de una sede. */
public class ServicioInventario {

    private final InventarioLocal inventario;
    private final CatalogoGlobal catalogo;
    private final IMensajeria mensajeria;

    /** Crea la fachada con el inventario, catálogo y mensajería de una sede. */
    public ServicioInventario(InventarioLocal inventario, CatalogoGlobal catalogo, IMensajeria mensajeria) {
        if (inventario == null || catalogo == null || mensajeria == null) {
            throw new IllegalArgumentException("Inventario, catálogo y mensajería no pueden ser null");
        }
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.mensajeria = mensajeria;
    }

    /** Consulta las existencias locales en un arreglo ordenado. */
    public synchronized ExistenciaInventario[] consultarInventarioLocal() {
        return inventario.listarExistencias();
    }

    /** Consulta el catálogo global y su disponibilidad conocida por sede. */
    public synchronized ProductoCatalogo[] consultarCatalogoGlobal() {
        return catalogo.listarCatalogo();
    }

    /** Agrega unidades locales y publica el nuevo stock. */
    public synchronized int agregarUnidades(String codigoProducto, int cantidad) {
        int stockNuevo = inventario.agregarUnidades(codigoProducto, cantidad);
        publicarStock(codigoProducto, stockNuevo);
        return stockNuevo;
    }

    /** Descuenta unidades locales y publica el nuevo stock. */
    public synchronized int descontar(String codigoProducto, int cantidad) {
        int stockNuevo = inventario.descontar(codigoProducto, cantidad);
        publicarStock(codigoProducto, stockNuevo);
        return stockNuevo;
    }

    private void publicarStock(String codigoProducto, int stockNuevo) {
        try {
            mensajeria.publicarActualizacionStock(normalizarCodigo(codigoProducto), stockNuevo);
        } catch (RuntimeException error) {
            throw new ServicioInventarioException("El stock local se actualizó, pero no se pudo publicar "
                    + "el cambio del producto " + codigoProducto, error);
        }
    }

    private String normalizarCodigo(String codigo) {
        String recortado = codigo.trim();
        StringBuilder resultado = new StringBuilder(recortado.length());
        for (int i = 0; i < recortado.length(); i++) {
            resultado.append(Character.toUpperCase(recortado.charAt(i)));
        }
        return resultado.toString();
    }
}
