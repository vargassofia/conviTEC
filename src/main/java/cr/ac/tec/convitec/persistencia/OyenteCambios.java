package cr.ac.tec.convitec.persistencia;

/** Notifica que el estado persistible cambió. */
@FunctionalInterface
public interface OyenteCambios {
    void alCambiar();
}
