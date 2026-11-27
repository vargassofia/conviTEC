package cr.ac.tec.convitec.persistencia;

/** Registro JSON de metadatos de la sede. */
public final class RegistroMeta {
    public int version = 1;
    public int contadorIds;

    public RegistroMeta() {
    }

    public RegistroMeta(int contadorIds) {
        if (contadorIds < 0) {
            throw new PersistenciaException("El contador de ids no puede ser negativo");
        }
        this.contadorIds = contadorIds;
    }

    public static RegistroMeta desdeModelo(int contadorIds) {
        return new RegistroMeta(contadorIds);
    }

    public int haciaContador() {
        if (version != 1 || contadorIds < 0) {
            throw new PersistenciaException("Metadatos inconsistentes o versión desconocida");
        }
        return contadorIds;
    }
}
