package cr.ac.tec.convitec.catalogo;

/** Stock conocido de un producto en una sede. */
public final class DisponibilidadSede {

    private final String codigoSede;
    private final int cantidadDisponible;

    /** Crea el registro de disponibilidad de una sede. */
    public DisponibilidadSede(String codigoSede, int cantidadDisponible) {
        this.codigoSede = codigoSede;
        this.cantidadDisponible = cantidadDisponible;
    }

    /** Devuelve el código de la sede. */
    public String getCodigoSede() {
        return codigoSede;
    }

    /** Devuelve el último stock conocido. */
    public int getCantidadDisponible() {
        return cantidadDisponible;
    }
}
