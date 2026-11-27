package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.modelo.Existencia;

/** Registro JSON de una existencia local. */
public final class RegistroExistencia {
    public int version = 1;
    public String codigo;
    public int disponible;
    public int reservado;

    public RegistroExistencia() {
    }

    public static RegistroExistencia desdeModelo(Existencia existencia) {
        if (existencia == null) {
            throw new PersistenciaException("No se puede persistir una existencia null");
        }
        RegistroExistencia registro = new RegistroExistencia();
        registro.codigo = existencia.getCodigoProducto();
        registro.disponible = existencia.getCantidadDisponible();
        registro.reservado = existencia.getCantidadReservada();
        return registro;
    }

    public Existencia haciaModelo() {
        validarVersion();
        try {
            return new Existencia(codigo, disponible, reservado);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Existencia inconsistente: " + e.getMessage(), e);
        }
    }

    void validarVersion() {
        if (version != 1) {
            throw new PersistenciaException("Versión de existencia desconocida: " + version);
        }
    }
}
