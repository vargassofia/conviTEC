package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.modelo.Producto;

/** Registro JSON de un producto del catálogo. */
public final class RegistroProducto {
    public int version = 1;
    public String codigo;
    public String nombre;
    public String descripcion;
    public String categoria;
    public double precio;

    public RegistroProducto() {
    }

    public static RegistroProducto desdeModelo(Producto producto) {
        if (producto == null) {
            throw new PersistenciaException("No se puede persistir un producto null");
        }
        RegistroProducto registro = new RegistroProducto();
        registro.codigo = producto.getCodigo();
        registro.nombre = producto.getNombre();
        registro.descripcion = producto.getDescripcion();
        registro.categoria = producto.getCategoria();
        registro.precio = producto.getPrecio();
        return registro;
    }

    public Producto haciaModelo() {
        if (version != 1) {
            throw new PersistenciaException("Versión de producto desconocida: " + version);
        }
        try {
            return new Producto(codigo, nombre, descripcion, categoria, precio);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Producto inconsistente: " + e.getMessage(), e);
        }
    }
}
