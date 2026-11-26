package cr.ac.tec.convitec.modelo;

/**
 * Producto inmutable del catálogo, compartido por todas las sedes.
 */
public final class Producto {

    private final String codigo;
    private final String nombre;
    private final String descripcion;
    private final String categoria;
    private final double precio;

    /**
     * Crea un producto validando sus datos y normalizando el código.
     *
     * @throws IllegalArgumentException si un dato obligatorio es inválido o el precio no es positivo
     */
    public Producto(String codigo, String nombre, String descripcion, String categoria, double precio) {
        this.codigo = normalizarCodigo(codigo);
        this.nombre = validarTextoObligatorio(nombre, "El nombre");
        this.descripcion = descripcion == null ? "" : descripcion.trim();
        this.categoria = validarTextoObligatorio(categoria, "La categoría");
        validarPrecio(precio);
        this.precio = precio;
    }

    /** Devuelve el código normalizado del producto. */
    public String getCodigo() {
        return codigo;
    }

    /** Devuelve el nombre del producto. */
    public String getNombre() {
        return nombre;
    }

    /** Devuelve la descripción del producto. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Devuelve la categoría del producto. */
    public String getCategoria() {
        return categoria;
    }

    /** Devuelve el precio de venta en colones. */
    public double getPrecio() {
        return precio;
    }

    /** Devuelve un producto nuevo con el precio indicado. */
    public Producto conPrecio(double nuevoPrecio) {
        return new Producto(codigo, nombre, descripcion, categoria, nuevoPrecio);
    }

    /** Compara este producto con otro por código, sin distinguir mayúsculas. */
    public int compararPorCodigo(Producto otro) {
        if (otro == null) {
            throw new IllegalArgumentException("El producto a comparar no puede ser null");
        }
        return codigo.compareToIgnoreCase(otro.codigo);
    }

    /** Compara productos únicamente por su código normalizado. */
    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Producto otro)) {
            return false;
        }
        return codigo.equals(otro.codigo);
    }

    /** Devuelve un código hash basado únicamente en el código. */
    @Override
    public int hashCode() {
        return codigo.hashCode();
    }

    /** Devuelve una representación legible del código, nombre y precio. */
    @Override
    public String toString() {
        return "Producto{codigo='" + codigo + "', nombre='" + nombre + "', precio=₡" + precio + "}";
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null) {
            throw new IllegalArgumentException("El código no puede ser null");
        }
        String recortado = codigo.trim();
        if (recortado.isEmpty()) {
            throw new IllegalArgumentException("El código no puede estar vacío");
        }

        StringBuilder normalizado = new StringBuilder(recortado.length());
        for (int i = 0; i < recortado.length(); i++) {
            normalizado.append(Character.toUpperCase(recortado.charAt(i)));
        }
        return normalizado.toString();
    }

    private static String validarTextoObligatorio(String texto, String nombreCampo) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException(nombreCampo + " no puede ser null ni estar vacío");
        }
        return texto.trim();
    }

    private static void validarPrecio(double precio) {
        if (Double.isNaN(precio) || Double.isInfinite(precio) || precio <= 0) {
            throw new IllegalArgumentException("El precio debe ser un número finito mayor que cero");
        }
    }
}
