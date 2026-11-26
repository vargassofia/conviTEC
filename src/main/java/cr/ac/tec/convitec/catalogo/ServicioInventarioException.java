package cr.ac.tec.convitec.catalogo;

/** Error al publicar un cambio de stock después de actualizar el inventario local. */
public class ServicioInventarioException extends RuntimeException {

    /** Crea la excepción indicando el cambio de stock que no pudo publicarse. */
    public ServicioInventarioException(String mensaje, RuntimeException causa) {
        super(mensaje, causa);
    }
}
