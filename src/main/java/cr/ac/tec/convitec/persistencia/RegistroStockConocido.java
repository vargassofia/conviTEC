package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.DisponibilidadSede;

/** Registro JSON del stock más reciente conocido de una sede. */
public final class RegistroStockConocido {
    public int version = 1;
    public String codigoSede;
    public String codigoProducto;
    public int stock;

    public RegistroStockConocido() {
    }

    public static RegistroStockConocido desdeModelo(String sede, String codigo, int stock) {
        if (sede == null || sede.isBlank() || codigo == null || codigo.isBlank() || stock < 0) {
            throw new PersistenciaException("Stock conocido inconsistente");
        }
        RegistroStockConocido registro = new RegistroStockConocido();
        registro.codigoSede = sede;
        registro.codigoProducto = codigo;
        registro.stock = stock;
        return registro;
    }

    public DisponibilidadSede haciaModelo() {
        validar();
        return new DisponibilidadSede(codigoSede, stock);
    }

    public void validar() {
        if (version != 1 || codigoSede == null || codigoSede.isBlank()
                || codigoProducto == null || codigoProducto.isBlank() || stock < 0) {
            throw new PersistenciaException("Registro de stock conocido inconsistente");
        }
    }
}
